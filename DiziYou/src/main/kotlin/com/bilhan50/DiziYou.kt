// ! Bu araç @bilhan50 tarafından | @bilhan50 için yazılmıştır.

package com.bilhan50

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup

class DiziYou : MainAPI() {
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
    override var mainUrl              = "https://www.diziyou.one"
    override var name                 = "DiziYou"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.TvSeries)

    // * yedek domain: ana domain cokerse otomatik gecis yapilir
    private val yedekDomainler = listOf(
        "https://www.diziyou.net",
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
        "/dizi-arsivi/page/SAYFA/?tur=Aile"                 to "Aile",
        "/dizi-arsivi/page/SAYFA/?tur=Aksiyon"              to "Aksiyon",
        "/dizi-arsivi/page/SAYFA/?tur=Animasyon"            to "Animasyon",
        "/dizi-arsivi/page/SAYFA/?tur=Belgesel"             to "Belgesel",
        "/dizi-arsivi/page/SAYFA/?tur=Bilim+Kurgu"          to "Bilim Kurgu",
        "/dizi-arsivi/page/SAYFA/?tur=Dram"                 to "Dram",
        "/dizi-arsivi/page/SAYFA/?tur=Fantazi"              to "Fantazi",
        "/dizi-arsivi/page/SAYFA/?tur=Gerilim"              to "Gerilim",
        "/dizi-arsivi/page/SAYFA/?tur=Gizem"                to "Gizem",
        "/dizi-arsivi/page/SAYFA/?tur=Komedi"               to "Komedi",
        "/dizi-arsivi/page/SAYFA/?tur=Korku"                to "Korku",
        "/dizi-arsivi/page/SAYFA/?tur=Macera"               to "Macera",
        "/dizi-arsivi/page/SAYFA/?tur=Sava%C5%9F"           to "Savaş",
        "/dizi-arsivi/page/SAYFA/?tur=Su%C3%A7"             to "Suç",
        "/dizi-arsivi/page/SAYFA/?tur=Vah%C5%9Fi+Bat%C4%B1" to "Vahşi Batı"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url      = "${siteUrl()}${request.data.replace("SAYFA", "$page")}"
        val document = app.get(url, interceptor = interceptor).document
        val home     = document.select("div.single-item").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("div#categorytitle a")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("div#categorytitle a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/?s=${query}", interceptor = interceptor).document

        return document.select("div.incontent div#list-series").mapNotNull { it.toMainPageResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, interceptor = interceptor).document

        val title           = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster          = fixUrlNull(document.selectFirst("div.category_image img")?.attr("src"))
        val description     = document.selectFirst("div.diziyou_desc")?.ownText()?.trim()
        val year            = document.selectFirst("span.dizimeta:contains(Yapım Yılı)")?.nextSibling()?.toString()?.trim()?.toIntOrNull()
        val tags            = document.select("div.genres a").map { it.text() }
        val rating          = document.selectFirst("span.dizimeta:contains(IMDB)")?.nextSibling()?.toString()?.trim()?.toScore()
        val actors          = document.selectFirst("span.dizimeta:contains(Oyuncular)")?.nextSibling()?.toString()?.trim()?.split(", ")?.map { Actor(it) }
        val trailer         = document.selectFirst("iframe.trailer-video")?.attr("src")

        val episodes = document.select("div.bolumust").mapNotNull {
            val epName    = it.selectFirst("div.baslik")?.ownText()?.trim() ?: return@mapNotNull null
            val epHref    = it.closest("a")?.attr("href")?.let { href -> fixUrlNull(href) } ?: return@mapNotNull null
            val epEpisode = Regex("""(\d+)\. Bölüm""").find(epName)?.groupValues?.get(1)?.toIntOrNull()
            val epSeason  = Regex("""(\d+)\. Sezon""").find(epName)?.groupValues?.get(1)?.toIntOrNull() ?: 1

            newEpisode(epHref) {
                this.name = it.selectFirst("div.bolumismi")?.text()?.trim()?.replace(Regex("""[()]"""), "")?.trim() ?: epName
                this.season = epSeason
                this.episode = epEpisode
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot      = description
            this.year      = year
            this.tags      = tags
            this.score    = rating
            addActors(actors)
            addTrailer(trailer)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("DZY", "data » $data")
        val document = app.get(data, interceptor = interceptor).document

        val itemId     = document.selectFirst("iframe#diziyouPlayer")?.attr("src")?.split("/")?.lastOrNull()?.substringBefore(".html") ?: return false
        Log.d("DZY", "itemId » $itemId")

        val subTitles  = mutableListOf<DiziyouSubtitle>()
        val streamUrls = mutableListOf<DiziyouStream>()
        val storage    = mainUrl.replace("www", "storage")

        document.select("span.diziyouOption").forEach {
            val optId   = it.attr("id")

            if (optId == "turkceAltyazili") {
                subTitles.add(DiziyouSubtitle("Turkish", "${storage}/subtitles/${itemId}/tr.vtt"))
                streamUrls.add(DiziyouStream("Orjinal Dil", "${storage}/episodes/${itemId}/play.m3u8"))
            }

            if (optId == "ingilizceAltyazili") {
                subTitles.add(DiziyouSubtitle("English", "${storage}/subtitles/${itemId}/en.vtt"))
                streamUrls.add(DiziyouStream("Orjinal Dil", "${storage}/episodes/${itemId}/play.m3u8"))
            }

            if (optId == "turkceDublaj") {
                streamUrls.add(DiziyouStream("Türkçe Dublaj", "${storage}/episodes/${itemId}_tr/play.m3u8"))
            }
        }

        for (sub in subTitles) {
            subtitleCallback.invoke(
                SubtitleFile(
                    lang = sub.name,
                    url  = fixUrl(sub.url)
                )
            )
        }

        for (stream in streamUrls) {
            callback.invoke(
                newExtractorLink(
                    source = stream.name,
                    name = stream.name,
                    url = stream.url
                ) {
                    this.referer = "${mainUrl}/"
                    this.quality = Qualities.Unknown.value
                    this.type = ExtractorLinkType.M3U8
                }
            )
        }

        return true
    }

    data class DiziyouSubtitle(val name: String, val url: String)
    data class DiziyouStream(val name: String, val url: String)
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
