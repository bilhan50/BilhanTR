// ! https://github.com/hexated/cloudstream-extensions-hexated/blob/master/Hdfilmcehennemi/src/main/kotlin/com/hexated/Hdfilmcehennemi.kt

package com.bilhan50

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.Jsoup
import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.Interceptor
import okhttp3.Response

class HDFilmCehennemi : MainAPI() {
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
    override var mainUrl              = "https://www.hdfilmcehennemi.nl"
    override var name                 = "HDFilmCehennemi"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.Movie, TvType.TvSeries)

    // * yedek domain: ana domain cokerse otomatik gecis yapilir
    private val yedekDomainler = listOf(
        "https://www.hdfilmcehennemi.vip",
    )
    private var aktifDomain: String? = null

    private suspend fun siteUrl(): String {
        aktifDomain?.let { return it }
        for (d in listOf(mainUrl) + yedekDomainler) {
            try {
                val r = app.get("$d/", interceptor = interceptor, throwOnFailure = false)
                if (r.code == 200) {
                    aktifDomain = d
                    mainUrl     = d
                    return d
                }
            } catch (_: Throwable) {}
        }
        return mainUrl
    }

    override val mainPage = mainPageOf(
        ""                                            to "Yeni Eklenen Filmler",
        "/yabancidiziizle-2"                          to "Yeni Eklenen Diziler",
        "/category/tavsiye-filmler-izle2"             to "Tavsiye Filmler",
        "/imdb-7-puan-uzeri-filmler"                  to "IMDB 7+ Filmler",
        "/en-cok-yorumlananlar-1"                     to "En Çok Yorumlananlar",
        "/en-cok-begenilen-filmleri-izle"             to "En Çok Beğenilenler",
        "/tur/aile-filmleri-izleyin-6"                to "Aile Filmleri",
        "/tur/aksiyon-filmleri-izleyin-3"             to "Aksiyon Filmleri",
        "/tur/animasyon-filmlerini-izleyin-4"         to "Animasyon Filmleri",
        "/tur/belgesel-filmlerini-izle-1"             to "Belgesel Filmleri",
        "/tur/bilim-kurgu-filmlerini-izleyin-2"       to "Bilim Kurgu Filmleri",
        "/tur/komedi-filmlerini-izleyin-1"            to "Komedi Filmleri",
        "/tur/korku-filmlerini-izle-2/"               to "Korku Filmleri",
        "/tur/romantik-filmleri-izle-1"               to "Romantik Filmleri"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${siteUrl()}${request.data}", interceptor = interceptor).document

        val home: List<SearchResponse>?

        home = document.select("div.section-content a.poster").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title     = this.selectFirst("strong.poster-title")?.text() ?: return null
        val href      = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val response      = app.get(
            "${mainUrl}/search?q=${query}",
            headers = mapOf("X-Requested-With" to "fetch"), interceptor = interceptor
        ).parsedSafe<Results>() ?: return emptyList()
        val searchResults = mutableListOf<SearchResponse>()

        response.results.forEach { resultHtml ->
            val document = Jsoup.parse(resultHtml)

            val title     = document.selectFirst("h4.title")?.text() ?: return@forEach
            val href      = fixUrlNull(document.selectFirst("a")?.attr("href")) ?: return@forEach
            val posterUrl = fixUrlNull(document.selectFirst("img")?.attr("src")) ?: fixUrlNull(document.selectFirst("img")?.attr("data-src"))

            searchResults.add(
                newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl?.replace("/thumb/", "/list/") }
            )
        }

        return searchResults
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, interceptor = interceptor).document

        val title       = document.selectFirst("h1.section-title")?.text()?.substringBefore(" izle") ?: return null
        val poster      = fixUrlNull(document.select("aside.post-info-poster img.lazyload").lastOrNull()?.attr("data-src"))
        val tags        = document.select("div.post-info-genres a").map { it.text() }
        val year        = document.selectFirst("div.post-info-year-country a")?.text()?.trim()?.toIntOrNull()
        val tvType      = if (document.select("div.seasons").isEmpty()) TvType.Movie else TvType.TvSeries
        val description = document.selectFirst("article.post-info-content > p")?.text()?.trim()
        val rating      = document.selectFirst("div.post-info-imdb-rating span")?.text()?.substringBefore("(")?.trim()?.toScore()
        val actors      = document.select("div.post-info-cast a").map {
            Actor(it.selectFirst("strong")!!.text(), it.select("img").attr("data-src"))
        }

        val recommendations = document.select("div.section-slider-container div.slider-slide").mapNotNull {
                val recName      = it.selectFirst("a")?.attr("title") ?: return@mapNotNull null
                val recHref      = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
                val recPosterUrl = fixUrlNull(it.selectFirst("img")?.attr("data-src")) ?: fixUrlNull(it.selectFirst("img")?.attr("src"))

                newTvSeriesSearchResponse(recName, recHref, TvType.TvSeries) {
                    this.posterUrl = recPosterUrl
                }
            }

        return if (tvType == TvType.TvSeries) {
            val trailer  = document.selectFirst("div.post-info-trailer button")?.attr("data-modal")?.substringAfter("trailer/")?.let { "https://www.youtube.com/embed/$it" }
            val episodes = document.select("div.seasons-tab-content a").mapNotNull {
                val epName    = it.selectFirst("h4")?.text()?.trim() ?: return@mapNotNull null
                val epHref    = fixUrlNull(it.attr("href")) ?: return@mapNotNull null
                val epEpisode = Regex("""(\d+)\. ?Bölüm""").find(epName)?.groupValues?.get(1)?.toIntOrNull()
                val epSeason  = Regex("""(\d+)\. ?Sezon""").find(epName)?.groupValues?.get(1)?.toIntOrNull() ?: 1

                newEpisode(epHref) {
                    this.name = epName
                    this.season = epSeason
                    this.episode = epEpisode
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl       = poster
                this.year            = year
                this.plot            = description
                this.tags            = tags
                this.score          = rating
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
            }
        } else {
            val trailer = document.selectFirst("div.post-info-trailer button")?.attr("data-modal")?.substringAfter("trailer/")?.let { "https://www.youtube.com/embed/$it" }

            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl       = poster
                this.year            = year
                this.plot            = description
                this.tags            = tags
                this.score          = rating
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
            }
        }
    }

    private suspend fun invokeLocalSource(source: String, url: String, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit ) {
        val doc    = app.get(url, referer = "${mainUrl}/", interceptor = interceptor).document
        val script = doc.select("script").map { it.data() }
            .filter { it.contains("sources:") || it.contains("tracks: [") }
        if (script.isEmpty()) return

        val videoData = script.firstNotNullOfOrNull { extractStreamUrl(it) } ?: run {
            Log.d("HDCH", "stream cozulemedi » $url")
            return
        }
        val subData = script.firstOrNull { it.contains("tracks: [") }
            ?.substringAfter("tracks: [")?.substringBefore("]")

        callback.invoke(
            newExtractorLink(
                source = source,
                name = source,
                url = videoData
            ) {
                this.referer = "${mainUrl}/"
                this.quality = Qualities.Unknown.value
                this.type    = ExtractorLinkType.M3U8
            }
        )

        if (!subData.isNullOrBlank()) {
            AppUtils.tryParseJson<List<SubSource>>("[${subData}]")?.filter { it.kind == "captions" }?.map {
                subtitleCallback.invoke(
                    SubtitleFile(it.label.toString(), fixUrl(it.file.toString()))
                )
            }
        }
    }

    /** Base64 cozup latin1 metin olarak dondurur; cozulemezse bos string. */
    private fun decodeBase64Latin1(str: String): String {
        val pad = (4 - str.length % 4) % 4
        return try {
            String(
                android.util.Base64.decode(str + "=".repeat(pad), android.util.Base64.DEFAULT),
                Charsets.ISO_8859_1
            )
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * Sitenin su anki video sifrelemesi: parca listesinden iki anahtari ayirir,
     * ROT / ters cevir / base64 adimlarini uygular, Fisher-Yates karistirir ve
     * XOR ile gercek .m3u8 adresini cikarir.
     */
    private fun decodePayload(parts: List<String>): String {
        val list  = parts.toMutableList()
        val aik4z = list.size - 2
        if (aik4z < 0) return ""
        val ozqzr = aik4z % 7
        val ft0q0 = 8 + (aik4z % 5)
        if (ft0q0 >= list.size || ozqzr >= list.size) return ""

        val t2o  = list.removeAt(ft0q0)
        val xtqz = list.removeAt(ozqzr)
        var uvhq = list.joinToString("")

        if (xtqz.length > 4096) uvhq = decodeBase64Latin1(uvhq)

        var xbgh9 = 0
        var hqbz  = 0
        for (i in xtqz.indices) {
            val xu25 = xtqz[i].code
            xbgh9 = (xbgh9 * 37 + xu25) % 241
            hqbz  = (hqbz + ((xu25 shl 1) xor i)) and 255
        }

        val mlr3o = (xbgh9 * 3 + hqbz) % 256
        val gjy   = (hqbz % 11) + 5
        var euerq = ((hqbz * 251 + xbgh9) % 65519) + 1

        for (i in t2o.length - 1 downTo 0) {
            val cmd = t2o[i]
            when (cmd) {
                '7' -> uvhq = decodeBase64Latin1(uvhq)
                '3' -> uvhq = uvhq.reversed()
                else -> {
                    val rot = (26 - ((cmd.code - 96) % 26)) % 26
                    val sb  = StringBuilder(uvhq.length)
                    for (ch in uvhq) {
                        val c = ch.code
                        sb.append(
                            when {
                                ch in 'A'..'Z' -> ((c - 65 + rot) % 26 + 65).toChar()
                                ch in 'a'..'z' -> ((c - 97 + rot) % 26 + 97).toChar()
                                else           -> ch
                            }
                        )
                    }
                    uvhq = sb.toString()
                }
            }
        }

        if (t2o.length > 2048) uvhq = uvhq.reversed()

        val finalLen = uvhq.length
        val gm7n = IntArray(finalLen)
        for (i in finalLen - 1 downTo 1) {
            euerq  = (euerq * 97 + 41) % 65519
            gm7n[i] = euerq % (i + 1)
        }

        val dowxc = uvhq.toCharArray()
        for (i in 1 until finalLen) {
            val w   = gm7n[i]
            val tmp = dowxc[i]
            dowxc[i] = dowxc[w]
            dowxc[w] = tmp
        }
        uvhq = String(dowxc)

        var uo2b0 = mlr3o
        val julf  = StringBuilder(uvhq.length)
        for (ch in uvhq) {
            val xu25 = ch.code
            uo2b0 = (uo2b0 * 5 + gjy) % 256
            julf.append((xu25 xor uo2b0).toChar())
            uo2b0 = (uo2b0 + xu25) % 256
        }
        return julf.toString()
    }

    /** Sayfadaki gizli video adresini cozer; bulamazsa null dondurur. */
    private fun extractStreamUrl(html: String): String? {
        if (html.isBlank()) return null

        // 1) dogrudan yazilmis dosya adresi
        Regex("""file:\s*["']([^"']+\.(?:m3u8|mp4|txt)[^"']*)["']""")
            .find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }?.let { return it }

        // 2) Dean Edwards ile paketlenmis kodu coz; her iki yolu da dene
        val variants = LinkedHashSet<String>()
        variants.add(html)
        listOf(getAndUnpack(html), runCatching { JsUnpacker(html).unpack() }.getOrNull()).forEach { u ->
            if (!u.isNullOrBlank()) variants.add(html + "\n" + u)
        }

        for (full in variants) {
            // 3) sources: [{file: <degisken>}]
            val varName = Regex("""sources:\s*\[\{file:\s*([a-zA-Z0-9_]+)""")
                .findAll(full).map { it.groupValues[1] }.firstOrNull { it != "atob" } ?: continue

            // 4) var <degisken> = func("...".split("..."));
            Regex("""var\s+$varName\s*=\s*[a-zA-Z0-9_]+\s*\(\s*["']([^"']+)["']\.split\(\s*["']([^"']+)["']\s*\)\s*\);""")
                .find(full)?.let { m ->
                    val decoded = decodePayload(m.groupValues[1].split(m.groupValues[2]))
                    if (decoded.startsWith("http")) return decoded
                }

            // 5) jenerik tarama
            Regex("""var\s+[a-zA-Z0-9_]+\s*=\s*[a-zA-Z0-9_]+\s*\(\s*["']([^"']{100,})["']\.split\(\s*["']([|\^*@#~])["']\s*\)\s*\);""")
                .findAll(full).forEach { m ->
                    val parts = m.groupValues[1].split(m.groupValues[2])
                    if (parts.size >= 10) {
                        val decoded = decodePayload(parts)
                        if (decoded.startsWith("http")) return decoded
                    }
                }
        }

        return null
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit ): Boolean {
        Log.d("HDCH", "data » $data")
        val document = app.get(data, interceptor = interceptor).document

        document.select("div.alternative-links").map { element ->
            element to element.attr("data-lang").uppercase()
        }.forEach { (element, langCode) ->
            element.select("button.alternative-link").map { button ->
                button.text().replace("(HDrip Xbet)", "").trim() + " $langCode" to button.attr("data-video")
            }.forEach { (source, videoID) ->
                val apiGet = app.get(
                    "${mainUrl}/video/$videoID/",
                    headers = mapOf(
                        "Content-Type"     to "application/json",
                        "X-Requested-With" to "fetch"
                    ),
                    referer = data, interceptor = interceptor
                ).text

                var iframe = Regex("""data-src=\\"([^"]+)""").find(apiGet)?.groupValues?.get(1)!!.replace("\\", "")
                if (iframe.contains("?rapidrame_id=")) {
                    iframe = "${mainUrl}/playerr/" + iframe.substringAfter("?rapidrame_id=")
                }

                Log.d("HDCH", "$source » $videoID » $iframe")
                invokeLocalSource(source, iframe, subtitleCallback, callback)
            }
        }

        return true
    }

    private data class SubSource(
        @JsonProperty("file")  val file: String?  = null,
        @JsonProperty("label") val label: String? = null,
        @JsonProperty("kind")  val kind: String?  = null
    )

    data class Results(
        @JsonProperty("results") val results: List<String> = arrayListOf()
    )
}


private fun String?.toScore(): com.lagradost.cloudstream3.Score? {
    val raw = this?.replace(" ", "")?.replace(",", ".")?.trim()?.toDoubleOrNull() ?: return null
    val v   = kotlin.math.abs(raw)
    return when {
        v <= 10.0  -> com.lagradost.cloudstream3.Score.from10(v)
        v <= 100.0 -> com.lagradost.cloudstream3.Score.from100(v)
        else       -> com.lagradost.cloudstream3.Score.from100(v / 1000.0)
    }
}
