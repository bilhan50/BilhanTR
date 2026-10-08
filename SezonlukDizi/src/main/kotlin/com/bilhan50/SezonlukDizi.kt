// ! Bu araç @bilhan50 tarafından | @bilhan50 için yazılmıştır.

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

class SezonlukDizi : MainAPI() {
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
    override var mainUrl              = "https://sezonlukdizi.cc"
    override var name                 = "SezonlukDizi"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "${mainUrl}/diziler.asp?siralama_tipi=id&s="          to "Son Eklenenler",
        "${mainUrl}/diziler.asp?siralama_tipi=id&tur=mini&s=" to "Mini Diziler",
        "${mainUrl}/diziler.asp?siralama_tipi=id&kat=2&s="    to "Yerli Diziler",
        "${mainUrl}/diziler.asp?siralama_tipi=id&kat=1&s="    to "Yabancı Diziler",
        "${mainUrl}/diziler.asp?siralama_tipi=id&kat=3&s="    to "Asya Dizileri",
        "${mainUrl}/diziler.asp?siralama_tipi=id&kat=4&s="    to "Animasyonlar",
        "${mainUrl}/diziler.asp?siralama_tipi=id&kat=5&s="    to "Animeler",
        "${mainUrl}/diziler.asp?siralama_tipi=id&kat=6&s="    to "Belgeseller",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}${page}", interceptor = interceptor).document
        val home     = document.select("div.afis a").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title     = this.selectFirst("div.description")?.text()?.trim() ?: return null
        val href      = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/diziler.asp?adi=${query}", interceptor = interceptor).document

        return document.select("div.afis a").mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, interceptor = interceptor).document

        val title       = document.selectFirst("div.header")?.text()?.trim() ?: return null
        val poster      = fixUrlNull(document.selectFirst("div.image img")?.attr("data-src")) ?: return null
        val year        = document.selectFirst("div.extra span")?.text()?.trim()?.split("-")?.first()?.toIntOrNull()
        val description = document.selectFirst("span#tartismayorum-konu")?.text()?.trim()
        val tags        = document.select("div.labels a[href*='tur']").mapNotNull { it.text().trim() }
        val rating      = document.selectFirst("div.dizipuani a div")?.text()?.trim()?.replace(",", ".").toScore()
        val duration    = document.selectXpath("//span[contains(text(), 'Dk.')]").text().trim().substringBefore(" Dk.").toIntOrNull()

        val endpoint    = url.split("/").last()

        val actorsReq  = app.get("${mainUrl}/oyuncular/${endpoint}", interceptor = interceptor).document
        val actors     = actorsReq.select("div.doubling div.ui").map {
            Actor(
                it.selectFirst("div.header")!!.text().trim(),
                fixUrlNull(it.selectFirst("img")?.attr("src"))
            )
        }


        val episodesReq = app.get("${mainUrl}/bolumler/${endpoint}", interceptor = interceptor).document
        val episodes    = mutableListOf<Episode>()
        for (sezon in episodesReq.select("table.unstackable")) {
            for (bolum in sezon.select("tbody tr")) {
                val epName    = bolum.selectFirst("td:nth-of-type(4) a")?.text()?.trim() ?: continue
                val epHref    = fixUrlNull(bolum.selectFirst("td:nth-of-type(4) a")?.attr("href")) ?: continue
                val epEpisode = bolum.selectFirst("td:nth-of-type(3)")?.text()?.substringBefore(".Bölüm")?.trim()?.toIntOrNull()
                val epSeason  = bolum.selectFirst("td:nth-of-type(2)")?.text()?.substringBefore(".Sezon")?.trim()?.toIntOrNull()

                episodes.add(newEpisode(epHref) {
                    this.name    = epName
                    this.season  = epSeason
                    this.episode = epEpisode
                })
            }
        }


        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.year      = year
            this.plot      = description
            this.tags      = tags
            this.score    = rating
            this.duration  = duration
            addActors(actors)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("SZD", "data » $data")
        val document = app.get(data, interceptor = interceptor).document
        val aspData = getAspData()
        val bid      = document.selectFirst("div#dilsec")?.attr("data-id") ?: return false
        Log.d("SZD", "bid » $bid")

        val altyaziResponse = app.post(
            "${mainUrl}/ajax/dataAlternatif${aspData.alternatif}.asp",
            headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
            data    = mapOf(
                "bid" to bid,
                "dil" to "1"
            ), interceptor = interceptor
        ).parsedSafe<Kaynak>()
        altyaziResponse?.takeIf { it.status == "success" }?.data?.forEach { veri ->
            Log.d("SZD", "dil»1 | veri.baslik » ${veri.baslik}")

            val veriResponse = app.post(
                "${mainUrl}/ajax/dataEmbed${aspData.embed}.asp",
                headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                data    = mapOf("id" to "${veri.id}"), interceptor = interceptor
            ).document

            val iframe = fixUrlNull(veriResponse.selectFirst("iframe")?.attr("src")) ?: return@forEach
            Log.d("SZD", "dil»1 | iframe » $iframe")

            kaynakAktar(iframe, "AltYazı - ${veri.baslik}", subtitleCallback, callback)
        }

        val dublajResponse = app.post(
            "${mainUrl}/ajax/dataAlternatif${aspData.alternatif}.asp",
            headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
            data    = mapOf(
                "bid" to bid,
                "dil" to "0"
            ), interceptor = interceptor
        ).parsedSafe<Kaynak>()
        dublajResponse?.takeIf { it.status == "success" }?.data?.forEach { veri ->
            Log.d("SZD", "dil»0 | veri.baslik » ${veri.baslik}")

            val veriResponse = app.post(
                "${mainUrl}/ajax/dataEmbed${aspData.embed}.asp",
                headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                data    = mapOf("id" to "${veri.id}"), interceptor = interceptor
            ).document

            val iframe = fixUrlNull(veriResponse.selectFirst("iframe")?.attr("src")) ?: return@forEach
            Log.d("SZD", "dil»0 | iframe » $iframe")

            kaynakAktar(iframe, "Dublaj - ${veri.baslik}", subtitleCallback, callback)
        }

        return true
    }

    /**
     * Kaynak iframe'ini cozup link olarak iletir.
     * vidmoly icin sayfada dogrudan HLS (`...master.m3u8?...`) bulunur,
     * digerleri CloudStream extractor'ina birakilir.
     */
    private suspend fun kaynakAktar(
        iframe: String, etiket: String,
        subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit
    ) {
        val url = if (iframe.startsWith("//")) "https:$iframe" else iframe

        if (url.contains("vidmoly")) {
            val headers = mapOf(
                "User-Agent"     to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36",
                "Sec-Fetch-Dest" to "iframe"
            )
            val kaynak = app.get(url, headers = headers, referer = "${mainUrl}/", interceptor = interceptor).text
            val m3u = Regex("""https?://[^"'\s\\]+\.m3u8[^"'\s\\]*""").find(kaynak)?.value
                ?: Regex("""file:\s*["']([^"']*\.m3u8[^"']*)["']""").find(kaynak)?.groupValues?.get(1)
            if (m3u.isNullOrBlank()) return

            Log.d("SZD", "vidmoly » $m3u")
            callback.invoke(
                newExtractorLink(
                    source = "VidMoly - $etiket",
                    name   = "VidMoly - $etiket",
                    url    = m3u
                ) {
                    this.referer = "https://vidmoly.net/"
                    this.quality = Qualities.Unknown.value
                    this.type    = ExtractorLinkType.M3U8
                }
            )
            return
        }

        loadExtractor(url, "${mainUrl}/", subtitleCallback) { link ->
            kotlinx.coroutines.runBlocking {
                callback.invoke(
                    newExtractorLink(
                        source = etiket,
                        name   = etiket,
                        url    = link.url
                    ) {
                        this.referer = link.referer
                        this.quality = link.quality
                        this.headers = link.headers
                        this.extractorData = link.extractorData
                        this.type = link.type
                    }
                )
            }
        }
    }

    //Helper function for getting the number (probably some kind of version?) after the dataAlternatif and dataEmbed
    private suspend fun getAspData() : AspData{
        val websiteCustomJavascript = app.get("${this.mainUrl}/js/site.min.js", interceptor = interceptor)
        val dataAlternatifAsp = Regex("""dataAlternatif(.*?).asp""").find(websiteCustomJavascript.text)?.groupValues?.get(1)
            .toString()
        val dataEmbedAsp = Regex("""dataEmbed(.*?).asp""").find(websiteCustomJavascript.text)?.groupValues?.get(1)
            .toString()
        return AspData(dataAlternatifAsp,dataEmbedAsp)
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
