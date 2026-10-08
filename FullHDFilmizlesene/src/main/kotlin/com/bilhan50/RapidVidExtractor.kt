// ! Bu araç @bilhan50 tarafından | @bilhan50 için yazılmıştır.

package com.bilhan50

import android.util.Base64
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup

open class RapidVid : ExtractorApi() {
    override val name            = "RapidVid"
    override val mainUrl         = "https://rapidvid.net"
    override val requiresReferer = true

    // ! CloudFlare bot korumasini asma
    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor      by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            val doc      = Jsoup.parse(response.peekBody(1024 * 1024).string())

            if (doc.text().contains("Just a moment")) {
                return cloudflareKiller.intercept(chain)
            }

            return response
        }
    }

    private fun pad(s: String): String {
        var p = s
        while (p.length % 4 != 0) p += "="
        return p
    }

    /**
     * Sitenin yeni video sifrelemesi:  av('<...')
     *   1) metni ters cevir, base64 coz
     *   2) "K9L" anahtariyla ((c % 5) + 1) kadar byte cikar
     *   3) tekrar base64 coz  -> gercek m3u8 adresi
     */
    private fun decodeAv(sifreli: String): String? {
        return try {
            val ters   = sifreli.reversed()
            val adim1  = String(Base64.decode(pad(ters), Base64.DEFAULT), Charsets.UTF_8)
            val anahtar = "K9L"
            val cikti  = StringBuilder()
            for (i in adim1.indices) {
                val r = anahtar[i % 3]
                cikti.append((adim1[i].code - (r.code % 5 + 1)).toChar())
            }
            String(Base64.decode(pad(cikti.toString()), Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Throwable) {
            Log.d("Kekik_${this.name}", "decodeAv hata » ${e.message}")
            null
        }
    }

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val extRef   = referer ?: ""
        val videoReq = app.get(url, referer = extRef, interceptor = interceptor).text

        // * Altyazilar (jwSetup.tracks -> kind:captions)
        val subUrls = mutableSetOf<String>()
        Regex("""captions","file":"([^"]+)","label":"([^"]+)"""").findAll(videoReq).forEach {
            val (subUrl, subLang) = it.destructured

            if (subUrl in subUrls) { return@forEach }
            subUrls.add(subUrl)

            subtitleCallback.invoke(
                SubtitleFile(
                    lang = subLang.replace("\\u0131", "ı").replace("\\u0130", "İ").replace("\\u00fc", "ü").replace("\\u00e7", "ç"),
                    url  = fixUrl(subUrl.replace("\\", ""))
                )
            )
        }

        // * Yeni format: "file": av('...')
        val avMatch = Regex("""file"\s*:\s*av\(\s*['"]([^'"]+)['"]\s*\)""").find(videoReq)
            ?: Regex("""\bav\(\s*['"]([^'"]+)['"]\s*\)""").find(videoReq)

        if (avMatch != null) {
            val decoded = decodeAv(avMatch.groupValues[1])
            if (!decoded.isNullOrBlank()) {
                Log.d("Kekik_${this.name}", "decoded » $decoded")

                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = decoded
                    ) {
                        this.referer = extRef
                        this.quality = Qualities.Unknown.value
                        this.type    = ExtractorLinkType.M3U8
                    }
                )
                return
            }
        }

        // * Eski format: file": "<kodlu>", ...
        var extractedValue = Regex("""file": "(.*)",""").find(videoReq)?.groupValues?.get(1)
        val decoded: String?

        if (extractedValue != null) {
            val bytes = extractedValue.split("\\x").filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray()
            decoded   = String(bytes, Charsets.UTF_8)
        } else {
            val evalJWSsetup = Regex("""\};\s*(eval\(function[\s\S]*?)var played = \d+;""").find(videoReq)?.groupValues?.get(1) ?: return
            @Suppress("LocalVariableName")
            val JWSsetup      = getAndUnpack(getAndUnpack(evalJWSsetup)).replace("\\\\", "\\")
            extractedValue  = Regex("""file":"(.*)","label""").find(JWSsetup)?.groupValues?.get(1)?.replace("\\\\x", "")

            val bytes = extractedValue?.chunked(2)?.map { it.toInt(16).toByte() }?.toByteArray()
            decoded   = bytes?.toString(Charsets.UTF_8) ?: return
        }

        Log.d("Kekik_${this.name}", "decoded » $decoded")

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = decoded
            ) {
                this.referer = extRef
                this.quality = Qualities.Unknown.value
                this.type    = ExtractorLinkType.M3U8
            }
        )
    }
}
