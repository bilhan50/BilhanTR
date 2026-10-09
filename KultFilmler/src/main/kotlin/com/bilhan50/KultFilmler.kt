// ! Bu araç @bilhan50 tarafından | @bilhan50 için yazılmıştır.

package com.bilhan50

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import android.util.Base64
import org.jsoup.Jsoup
import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.Interceptor
import okhttp3.Response

class KultFilmler : MainAPI() {
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
    override var mainUrl              = "https://kultfilmler.net"
    override var name                 = "KultFilmler"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie, TvType.TvSeries)

    // * yedek domain: ana domain cokerse otomatik gecis yapilir
    private val yedekDomainler = listOf(
        "https://kultfilmler.org",
    )
    private var aktifDomain: String? = null

    private suspend fun siteUrl(): String {
        aktifDomain?.let { return it }
        for (d in listOf(mainUrl) + yedekDomainler) {
            try {
                val r = app.get("$d/", interceptor = interceptor)
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
        "/page/"                                      to "Son Filmler",
        "/category/aile-filmleri-izle/page/"		    to "Aile",
        "/category/aksiyon-filmleri-izle/page/"	    to "Aksiyon",
        "/category/animasyon-filmleri-izle/page/"	    to "Animasyon",
        "/category/belgesel-izle/page/"			    to "Belgesel",
        "/category/bilim-kurgu-filmleri-izle/page/"   to "Bilim Kurgu",
        "/category/biyografi-filmleri-izle/page/"	    to "Biyografi",
        "/category/dram-filmleri-izle/page/"		    to "Dram",
        "/category/fantastik-filmleri-izle/page/"	    to "Fantastik",
        "/category/gerilim-filmleri-izle/page/"	    to "Gerilim",
        "/category/gizem-filmleri-izle/page/"		    to "Gizem",
        "/category/kara-filmleri-izle/page/"		    to "Kara",
        "/category/kisa-film-izle/page/"			    to "Kısa Metrajlı",
        "/category/komedi-filmleri-izle/page/"		to "Komedi",
        "/category/korku-filmleri-izle/page/"		    to "Korku",
        "/category/macera-filmleri-izle/page/"		to "Macera",
        "/category/muzik-filmleri-izle/page/"		    to "Müzik",
        "/category/polisiye-filmleri-izle/page/"	    to "Polisiye",
        "/category/politik-filmleri-izle/page/"	    to "Politik",
        "/category/romantik-filmleri-izle/page/"	    to "Romantik",
        "/category/savas-filmleri-izle/page/"		    to "Savaş",
        "/category/spor-filmleri-izle/page/"		    to "Spor",
        "/category/suc-filmleri-izle/page/"		    to "Suç",
        "/category/tarih-filmleri-izle/page/"		    to "Tarih",
        "/category/yerli-filmleri-izle/page/"		    to "Yerli"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${siteUrl()}${request.data}${page}", interceptor = interceptor).document
        val home     = document.select("a.mcard, div.movie-box").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        // * yeni tema: <a class="mcard" href="..."><div class="mbody"><div class="mtx"><h3>...
        // * eski tema: <div class="movie-box"><div class="name"><a href="...">
        val title = this.selectFirst("div.mtx h3")?.text()?.trim()
            ?: this.selectFirst("div.name a")?.text()?.trim()
            ?: this.selectFirst("img.pimg")?.attr("alt")?.trim()
            ?: return null

        val href = fixUrlNull(
            this.attr("href").ifBlank { this.selectFirst("div.name a")?.attr("href") ?: "" }
        ) ?: return null

        val posterUrl = fixUrlNull(this.selectFirst("img.pimg, div.img img")?.attr("src"))

        return if (href.contains("/dizi/")) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}?s=${query}", interceptor = interceptor).document

        return document.select("a.mcard, div.movie-box").mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, interceptor = interceptor).document

        // * yeni tema: h1.vtitle  |  eski tema: div.film h1 / h1.film
        val title       = document.selectFirst("h1.vtitle")?.text()?.trim()
            ?: document.selectFirst("div.film h1, h1.film")?.text()?.trim()
            ?: document.selectFirst("h1")?.text()?.trim()
            ?: return null
        val poster      = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val description = document.selectFirst("p.desc, #desc, div.description")?.text()?.trim()

        // * yeni temada film bilgileri "div.info" satirlari halinde
        val infoRows = document.select("div.info div.irow").associate {
            (it.selectFirst("div.ilabel")?.text() ?: "") to (it.selectFirst("div.ivalue, div.imdb")?.text() ?: "")
        }
        fun bilgi(vararg anahtar: String): String? = infoRows.entries
            .firstOrNull { e -> anahtar.any { e.key.contains(it, ignoreCase = true) } }
            ?.value?.trim()?.takeIf { it.isNotEmpty() }

        var tags = document.select("ul.post-categories a").map { it.text() }
        if (tags.isEmpty()) {
            tags = (bilgi("Tür") ?: "").split("•", ",", "/").map { it.trim() }.filter { it.isNotEmpty() }
        }
        val rating   = bilgi("IMDb")?.let { Regex("""\d+[.,]?\d*""").find(it)?.value }?.toScore()
        val year     = bilgi("Yıl")?.let { Regex("""(19|20)\d{2}""").find(it)?.value }?.toIntOrNull()
        val duration = bilgi("Süre")?.let { Regex("""\d+""").find(it)?.value }?.toIntOrNull()

        val recommendations = document.select("a.mcard, div.movie-box").mapNotNull { it.toSearchResult() }
        val actors          = document.select("a.cmember h5, [href*='oyuncular']")
            .mapNotNull { Actor(it.text().trim()).takeIf { a -> a.name.isNotBlank() } }
            .distinctBy { it.name }

        if (url.contains("/dizi/")) {
            tags  = document.select("div.category a").map { it.text() }

            val episodes = document.select("div.episode-box").mapNotNull {
                val epHref    = fixUrlNull(it.selectFirst("div.name a")?.attr("href")) ?: return@mapNotNull null
                val ssnDetail = it.selectFirst("span.episodetitle")?.ownText()?.trim() ?: return@mapNotNull null
                val epDetail  = it.selectFirst("span.episodetitle b")?.ownText()?.trim() ?: return@mapNotNull null
                val epName    = "$ssnDetail - $epDetail"
                val epSeason  = ssnDetail.substringBefore(". ").toIntOrNull()
                val epEpisode = epDetail.substringBefore(". ").toIntOrNull()

                newEpisode(epHref) {
                    this.name    = epName
                    this.season  = epSeason
                    this.episode = epEpisode
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl       = poster
                this.year            = year
                this.plot            = description
                this.tags            = tags
                this.score          = rating
                this.duration        = duration
                this.recommendations = recommendations
                addActors(actors)
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl       = poster
            this.year            = year
            this.plot            = description
            this.tags            = tags
            this.score          = rating
            this.duration        = duration
            this.recommendations = recommendations
            addActors(actors)
        }
    }

    private fun getIframe(sourceCode: String): String {
        // val atobKey = Regex("""atob\("(.*)"\)""").find(sourceCode)?.groupValues?.get(1) ?: return ""

        // return Jsoup.parse(String(Base64.decode(atobKey))).selectFirst("iframe")?.attr("src") ?: ""

        val atob = Regex("""PHA\+[0-9a-zA-Z+/=]*""").find(sourceCode)?.value ?: return ""

        val padding    = 4 - atob.length % 4
        val atobPadded = if (padding < 4) atob.padEnd(atob.length + padding, '=') else atob

        val iframe = Jsoup.parse(String(Base64.decode(atobPadded, Base64.DEFAULT), Charsets.UTF_8))

        return fixUrlNull(iframe.selectFirst("iframe")?.attr("src")) ?: ""
    }

    /** "//ok.ru/..." gibi kacisli veya protokol-gomulu kaynak adreslerini duzeltir */
    private fun kaynakAdresi(raw: String): String {
        var s = raw.replace("\\/", "/").trim()
        if (s.startsWith("//")) s = "https:$s"
        return s
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("KLT", "data » $data")
        val document = app.get(data, interceptor = interceptor).document
        val iframes  = mutableSetOf<String>()

        // * yeni tema: acik iframe + <script id="kf-srcdata"> icindeki JSON kaynaklari
        document.select("div.kf-embed iframe, div#player iframe").forEach {
            val src = it.attr("src").ifBlank { it.attr("data-src") }
            if (src.isNotBlank()) iframes.add(kaynakAdresi(src))
        }

        val srcData = document.selectFirst("script#kf-srcdata")?.data()
        if (!srcData.isNullOrBlank()) {
            Regex("""src=\\"(.*?)\\"""").findAll(srcData).forEach { m ->
                val src = kaynakAdresi(m.groupValues[1])
                if (src.isNotBlank()) iframes.add(src)
            }
        }

        // * eski tema: base64 kodlu iframe ve alternatif kaynaklar
        getIframe(document.html()).takeIf { it.isNotBlank() }?.let { iframes.add(it) }

        document.select("div.parts-middle").forEach {
            val alternatif = it.selectFirst("a")?.attr("href")
            if (alternatif != null) {
                val alternatifDocument = app.get(alternatif, interceptor = interceptor).document
                getIframe(alternatifDocument.html()).takeIf { f -> f.isNotBlank() }?.let { f -> iframes.add(f) }
            }
        }

        for (iframe in iframes) {
            Log.d("KLT", "iframe » $iframe")
            if (iframe.isBlank()) continue
            if (iframe.contains("vidmoly")) {
                val headers  = mapOf(
                    "User-Agent"     to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36",
                    "Sec-Fetch-Dest" to "iframe"
                )
                val iSource = app.get(iframe, headers=headers, referer="${mainUrl}/", interceptor = interceptor).text
                val m3uLink = Regex("""https?://[^"'\s\\]+\.m3u8[^"'\s\\]*""").find(iSource)?.value
                    ?: Regex("""file:\s*["']([^"']*\.m3u8[^"']*)["']""").find(iSource)?.groupValues?.get(1)
                if (m3uLink.isNullOrBlank()) continue

                Log.d("Kekik_VidMoly", "m3uLink » $m3uLink")

                callback.invoke(
                    newExtractorLink(
                        source = "VidMoly",
                        name = "VidMoly",
                        url = m3uLink
                    ) {
                        this.referer = "https://vidmoly.to/"
                        this.quality = Qualities.Unknown.value
                        // * vidmoly hls verir, adreste uzanti yok
                        this.type    = ExtractorLinkType.M3U8
                    }
                )
            } else {
                loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
            }
        }

        return true
    }
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
