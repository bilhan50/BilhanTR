// ! Bu eklenti @bilhan50 tarafindan | @bilhan50 icin yazilmistir.

package com.bilhan50

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup

class DiziRoll : MainAPI() {
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

    override var mainUrl              = "https://diziroll.club"
    override var name                 = "DiziRoll"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val hasSearch            = false
    override val supportedTypes       = setOf(TvType.TvSeries)

    // * /dizi-izle tum listeyi, /kesfet "kesfet" bolumunu dondurur
    override val mainPage = mainPageOf(
        "${mainUrl}/kesfet"    to "Kesfet",
        "${mainUrl}/dizi-izle" to "Tum Diziler",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data, interceptor = interceptor).document
        // * kartlar: <a href="https://diziroll.club/dizi/<slug>" title="Ad">
        val home     = document.select("a[href*='/dizi/'][title]").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home, false)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.attr("title").trim().ifEmpty { this.text().trim() }
        val href  = fixUrlNull(this.attr("href")) ?: return null

        // * navigo (SPA) linklerini temizle, sadece dizi sayfalari kalsin
        if (!href.contains("/dizi/")) return null
        if (href.count { it == '/' } > 4) return null

        val posterUrl = fixUrlNull(this.selectFirst("img")?.let {
            it.attr("data-src").ifEmpty { it.attr("src") }
        })

        return newTvSeriesSearchResponse(title.ifEmpty { href.substringAfterLast("/") }, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, interceptor = interceptor).document

        val h1 = document.selectFirst("h1")?.text()?.trim() ?: return null
        // * h1 "Reacher (2022)" formatinda; yili ayir
        val year   = Regex("""\((\d{4})\)""").find(h1)?.groups?.get(1)?.value?.toIntOrNull()
        val title  = h1.replace(Regex("""\s*\(\d{4}\)"""), "").trim()

        // * poster: buyuk boyutlu macellan gorseli tercih et
        val poster = fixUrlNull(
            document.selectFirst("img[data-src*='/648/960/']")?.attr("data-src")
                ?: document.selectFirst("img[data-src*='file.macellan.online/images/']")?.attr("data-src")
        )

        val description = document.selectFirst("meta[name=description]")?.attr("content")?.trim()
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

        // * bolumler: <a href=".../dizi/<slug>/sezon-N/bolum-M">
        val episodes = mutableListOf<Episode>()
        val gorulen  = mutableSetOf<String>()

        document.select("a[href*='/sezon-'][href*='/bolum-']").forEach { a ->
            val epUrl = fixUrlNull(a.attr("href")) ?: return@forEach
            if (epUrl in gorulen) return@forEach
            gorulen.add(epUrl)

            val sezonNo  = Regex("""/sezon-(\d+)""").find(epUrl)?.groups?.get(1)?.value?.toIntOrNull() ?: return@forEach
            val bolumNo  = Regex("""/bolum-(\d+)""").find(epUrl)?.groups?.get(1)?.value?.toIntOrNull() ?: return@forEach
            val epTitle  = a.text().trim().ifEmpty { "${sezonNo}. Sezon ${bolumNo}. Bolum" }

            episodes.add(newEpisode(epUrl) {
                this.name    = epTitle
                this.season  = sezonNo
                this.episode = bolumNo
            })
        }

        episodes.sortBy { (it.season ?: 0) * 1000 + (it.episode ?: 0) }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.year      = year
            this.plot      = description
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data, interceptor = interceptor).document
        val iframes  = mutableSetOf<String>()

        // * bolum sayfasindaki player iframe'i: //four.pichive.online/iframe.php?v=<id>
        val kaynaklar = document.select("iframe[src]").mapNotNull { fixUrlNull(it.attr("src")) }

        for (kaynak in kaynaklar) {
            if (kaynak in iframes) continue
            iframes.add(kaynak)

            Log.d("DZROLL", "iframe -> $kaynak")

            loadExtractor(fixUrl(kaynak), "$mainUrl/", subtitleCallback, callback)
        }

        return iframes.isNotEmpty()
    }
}
