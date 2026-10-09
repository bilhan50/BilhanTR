// ! Bu eklenti @bilhan50 tarafindan | @bilhan50 icin yazilmistir.

package com.bilhan50

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup

class SezonlukFilm : MainAPI() {
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

    override var mainUrl              = "https://sezonlukdizi.one"
    override var name                 = "SezonlukFilm"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "${mainUrl}/film-arsivi/" to "Film Arsivi",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url      = if (page > 1) "${request.data}sayfa/$page/" else request.data
        val document = app.get(url, interceptor = interceptor).document
        val home     = document.select("div.movie-box").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home, page < 2)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val baslik = this.selectFirst("div.film-ismi a")?.text()?.trim()
            ?: this.selectFirst("a[title]")?.attr("title")?.trim()
            ?: return null
        val href = fixUrlNull(
            this.selectFirst("div.film-ismi a")?.attr("href")
                ?: this.selectFirst("div.poster a")?.attr("href")
        ) ?: return null

        val posterUrl = fixUrlNull(this.selectFirst("img.lazy")?.let {
            it.attr("data-src").ifEmpty { it.attr("src") }
        })
        val year = this.selectFirst("div.film-yil")?.text()?.trim()?.toIntOrNull()

        return newMovieSearchResponse(baslik, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.year      = year
        }
    }

    // * WordPress REST API uzerinden arama
    override suspend fun search(query: String): List<SearchResponse> {
        val json = app.get(
            "${mainUrl}/wp-json/wp/v2/search?search=${java.net.URLEncoder.encode(query, "UTF-8")}&per_page=20",
            interceptor = interceptor
        ).text

        val sonuclar = mutableListOf<SearchResponse>()
        val gorulen  = mutableSetOf<String>()

        val urlDuzen = Regex(""""url":"(https:\\?/\\?/sezonlukdizi\.one\\?/[^"]+?)"""")
        val adDuzen  = Regex(""""title":"([^"]+)"""")

        val urlListe = urlDuzen.findAll(json).map { it.groups[1]!!.value.replace("\\/", "/") }.toList()
        val adListe  = adDuzen.findAll(json).map { it.groups[1]!!.value }.toList()

        urlListe.forEachIndexed { i, u ->
            val href = u.replace("\\/", "/")
            if (!href.endsWith("/") || href in gorulen) return@forEachIndexed
            if (!href.startsWith(mainUrl)) return@forEachIndexed
            gorulen.add(href)

            val ad = adListe.getOrNull(i)?.trim()?.ifEmpty { null }
                ?: href.trimEnd('/').substringAfterLast("/").replace('-', ' ')

            sonuclar.add(newMovieSearchResponse(ad, href, TvType.Movie))
        }

        return sonuclar
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, interceptor = interceptor).document

        // * <h1 class="title-border">Film Adi</h1>
        val title = document.selectFirst("div.singlecontent div.title h1")?.text()?.trim()
            ?: document.selectFirst("h1")?.text()?.trim()
            ?: return null

        // * <div class="film-afis"><img src="..."></div>
        val poster = fixUrlNull(
            document.selectFirst("div.film-afis img")?.let {
                it.attr("src").ifEmpty { it.attr("data-src") }
            }
        )

        // * orijinal ad: <div lang="en" class="bolum-ismi">
        val altTitle = document.selectFirst("div.bolum-ismi")?.text()?.trim()

        // * oyuncular: /oyuncu/<slug>/  yonetmen: /yonetmen/<slug>/
        val actors = document.select("a[href*='/oyuncu/']").mapNotNull {
            val ad = it.text().trim().ifEmpty { return@mapNotNull null }
            Actor(ad)
        }.distinctBy { it.name }

        val director = document.selectFirst("a[href*='/yonetmen/']")?.text()?.trim()

        // * puan: kartlardaki bolum-ust degeri detayda olmayabilir; guvenli sekilde dene
        val rating = document.selectFirst("div.elements div.bolum-ust")?.text()?.trim()?.replace(",", ".")?.toDoubleOrNull()

        return newMovieLoadResponse(title, url, TvType.Movie) {
            this.posterUrl = poster
            this.plot      = altTitle
            this.score     = rating?.let { Score.from10(it) }
            this.duration  = null
            addActors(actors)
            this.director  = director
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data, interceptor = interceptor).document
        val iframes  = mutableSetOf<String>()

        // * ana player: <div class="video-container" id="vast"><iframe src="https://videoseyred.in/embed/<id>">
        val kaynaklar = document.select("div#vast iframe[src], div.video-container iframe[src], iframe[src*='videoseyred']")
            .mapNotNull { fixUrlNull(it.attr("src")) }

        for (kaynak in kaynaklar) {
            if (kaynak in iframes) continue
            // * fragman (youtube) atlaniyor
            if (!kaynak.contains("videoseyred")) continue
            iframes.add(kaynak)

            Log.d("SFLM", "iframe -> $kaynak")

            loadExtractor(kaynak, "$mainUrl/", subtitleCallback, callback)
        }

        return iframes.isNotEmpty()
    }
}
