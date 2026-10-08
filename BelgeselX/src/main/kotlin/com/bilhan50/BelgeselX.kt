// ! Bu araç @bilhan50 tarafından | @bilhan50 için yazılmıştır.

package com.bilhan50

import java.util.Locale
import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup

class BelgeselX : MainAPI() {
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
    override var mainUrl              = "https://belgeselx.com"
    override var name                 = "BelgeselX"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Documentary, TvType.Movie)

    override val mainPage = mainPageOf(
        "${mainUrl}/konu/turk-tarihi-belgeselleri&page=" to "Türk Tarihi",
        "${mainUrl}/konu/tarih-belgeselleri&page="		 to "Tarih",
        "${mainUrl}/konu/seyehat-belgeselleri&page="	 to "Seyahat",
        "${mainUrl}/konu/seri-belgeseller&page="		 to "Seri",
        "${mainUrl}/konu/savas-belgeselleri&page="		 to "Savaş",
        "${mainUrl}/konu/sanat-belgeselleri&page="		 to "Sanat",
        "${mainUrl}/konu/psikoloji-belgeselleri&page="	 to "Psikoloji",
        "${mainUrl}/konu/polisiye-belgeselleri&page="	 to "Polisiye",
        "${mainUrl}/konu/otomobil-belgeselleri&page="	 to "Otomobil",
        "${mainUrl}/konu/nazi-belgeselleri&page="		 to "Nazi",
        "${mainUrl}/konu/muhendislik-belgeselleri&page=" to "Mühendislik",
        "${mainUrl}/konu/kultur-din-belgeselleri&page="	 to "Kültür Din",
        "${mainUrl}/konu/kozmik-belgeseller&page="		 to "Kozmik",
        "${mainUrl}/konu/hayvan-belgeselleri&page="		 to "Hayvan",
        "${mainUrl}/konu/eski-tarih-belgeselleri&page="	 to "Eski Tarih",
        "${mainUrl}/konu/egitim-belgeselleri&page="		 to "Eğitim",
        "${mainUrl}/konu/dunya-belgeselleri&page="		 to "Dünya",
        "${mainUrl}/konu/doga-belgeselleri&page="		 to "Doğa",
        "${mainUrl}/konu/bilim-belgeselleri&page="		 to "Bilim"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}${page}", interceptor = interceptor).document
        val home     = document.select("a.px-card, div.gen-movie-contain").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun String.toTitleCase(): String {
        val locale = Locale("tr", "TR")
        return this.split(" ").joinToString(" ") { word ->
            word.lowercase(locale).replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        // * yeni tema: <a class="px-card" href="..."><div class="px-card-title">...</div>
        val yeniHref = fixUrlNull(this.attr("href"))
        if (!yeniHref.isNullOrBlank()) {
            val yeniTitle = this.selectFirst("div.px-card-title")?.text()?.trim()?.toTitleCase()
                ?: this.selectFirst("img.px-card-img")?.attr("alt")?.trim()?.toTitleCase()
                ?: return null
            val yeniPoster = fixUrlNull(this.selectFirst("img.px-card-img")?.attr("src"))
            return newTvSeriesSearchResponse(yeniTitle, yeniHref, TvType.Documentary) { this.posterUrl = yeniPoster }
        }

        // * eski tema: div.gen-movie-contain > h3 a
        val title     = this.selectFirst("h3 a")?.text()?.trim()?.toTitleCase() ?: return null
        val href      = fixUrlNull(this.selectFirst("h3 a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.Documentary) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val cx = "016376594590146270301:iwmy65ijgrm" // ! Might change in the future

        val tokenResponse = app.get("https://cse.google.com/cse.js?cx=${cx}", interceptor = interceptor)
        val cseLibVersion = Regex("""cselibVersion": "(.*)"""").find(tokenResponse.text)?.groupValues?.get(1)
        val cseToken      = Regex("""cse_token": "(.*)"""").find(tokenResponse.text)?.groupValues?.get(1)

        val response = app.get("https://cse.google.com/cse/element/v1?rsz=filtered_cse&num=100&hl=tr&source=gcsc&cselibv=${cseLibVersion}&cx=${cx}&q=${query}&safe=off&cse_tok=${cseToken}&oq=${query}&callback=google.search.cse.api9969&rurl=https%3A%2F%2Fbelgeselx.com%2F", interceptor = interceptor)
        Log.d("BLX","Search result: ${response.text}")

        val titles     = Regex(""""titleNoFormatting": "(.*)"""").findAll(response.text).map { it.groupValues[1] }.toList()
        val urls       = Regex(""""url": "(.*)"""").findAll(response.text).map { it.groupValues[1] }.toList()
        val posterUrls = Regex(""""ogImage": "(.*)"""").findAll(response.text).map { it.groupValues[1] }.toList()

        val searchResponses = mutableListOf<TvSeriesSearchResponse>()

        for (i in titles.indices) {
            val title     = titles[i].split("İzle")[0].trim().toTitleCase()
            val url       = urls.getOrNull(i) ?: continue
            val posterUrl = posterUrls.getOrNull(i) ?: continue

            if(!url.contains("belgeseldizi")) continue
            searchResponses.add(newTvSeriesSearchResponse(title,url,TvType.Documentary) {
                this.posterUrl = posterUrl
            })
        }

        return searchResponses
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, interceptor = interceptor).document

        // * yeni tema: <h1> + og:* etiketleri  |  eski tema: h2.gen-title + gen-tv-show-top
        val title = document.selectFirst("h1")?.text()?.trim()?.toTitleCase()
            ?: document.selectFirst("h2.gen-title")?.text()?.trim()?.toTitleCase()
            ?: return null
        val poster = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
            ?: fixUrlNull(document.selectFirst("div.gen-tv-show-top img")?.attr("src"))
            ?: return null
        val description = document.selectFirst("[property='og:description']")?.attr("content")?.trim()
            ?: document.selectFirst("div.gen-single-tv-show-info p")?.text()?.trim()
        val tags = document.select("div.gen-socail-share a[href*='belgeselkanali']").map {
            it.attr("href").split("/").lastOrNull()?.replace("-", " ")?.toTitleCase() ?: ""
        }.filter { it.isNotBlank() }

        var episodes = emptyList<Episode>()

        // * yeni tema: bolum listesi oynatma sayfasindaki diziGetir(...) satirlarinda
        val izleUrl = document.selectFirst("a.px-ep-card")?.attr("href")?.ifBlank { null }
        if (!izleUrl.isNullOrBlank()) {
            val kayitlar = diziGetirAyikla(app.get(izleUrl, interceptor = interceptor).text)
            if (kayitlar.isNotEmpty()) {
                episodes = kayitlar.map { k ->
                    newEpisode("$izleUrl?bolum=${k.id}") {
                        this.name    = k.baslik
                        this.season  = k.sezon
                        this.episode = k.bolum
                    }
                }
            }
        }

        // * yeni tema ama diziGetir yoksa: kart uzerinden bolum listesi
        if (episodes.isEmpty()) {
            episodes = document.select("a.px-ep-card").mapIndexedNotNull { index, el ->
                val epHref = fixUrlNull(el.attr("href")) ?: return@mapIndexedNotNull null
                val epName = el.selectFirst("span.px-ep-title")?.text()?.trim() ?: return@mapIndexedNotNull null
                val meta   = el.selectFirst("span.px-ep-s")?.text() ?: ""
                newEpisode(epHref) {
                    this.name    = epName
                    this.season  = Regex("""S(\d+)""").find(meta)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    this.episode = Regex("""B(\d+)""").find(meta)?.groupValues?.get(1)?.toIntOrNull() ?: (index + 1)
                }
            }
        }

        // * eski tema: div.gen-movie-contain
        if (episodes.isEmpty()) {
            var counter = 0
            episodes = document.select("div.gen-movie-contain").mapNotNull {
                val epName     = it.selectFirst("div.gen-movie-info h3 a")?.text()?.trim() ?: return@mapNotNull null
                val epHref     = fixUrlNull(it.selectFirst("div.gen-movie-info h3 a")?.attr("href")) ?: return@mapNotNull null

                val seasonName = it.selectFirst("div.gen-single-meta-holder ul li")?.text()?.trim() ?: ""
                var epEpisode  = Regex("""Bölüm (\d+)""").find(seasonName)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val epSeason   = Regex("""Sezon (\d+)""").find(seasonName)?.groupValues?.get(1)?.toIntOrNull() ?: 1

                if (epEpisode == 0) {
                    epEpisode = counter++
                }

                newEpisode(epHref) {
                    this.name    = epName
                    this.season  = epSeason
                    this.episode = epEpisode
                }
            }
        }

        if (episodes.isEmpty()) return null

        return newTvSeriesLoadResponse(title, url, TvType.Documentary, episodes) {
            this.posterUrl = poster
            this.plot      = description
            this.tags      = tags
        }
    }

    /** Oynatma sayfasindaki diziGetir('id','ic1','ic2','ic3','baslik',...,'sezon','bolum',...) satirlari */
    private data class KaynakKayit(
        val id: String, val ic: List<String>, val baslik: String, val sezon: Int, val bolum: Int
    )

    private fun diziGetirAyikla(html: String): List<KaynakKayit> {
        val out = mutableListOf<KaynakKayit>()
        Regex("""diziGetir\((.+?)\)""").findAll(html).forEach { m ->
            val p = m.groupValues[1].split("','").map { it.trim('\'') }
            if (p.size < 9 || p[0].isBlank()) return@forEach
            out.add(
                KaynakKayit(
                    id     = p[0],
                    ic     = listOf(p.getOrElse(1) { "" }, p.getOrElse(2) { "" }, p.getOrElse(3) { "" }),
                    baslik = p[4],
                    sezon  = p[7].toIntOrNull() ?: 1,
                    bolum  = p[8].toIntOrNull() ?: (out.size + 1)
                )
            )
        }
        return out
    }

    /** srcMap = { '0':'new5','2':'new1', ... } */
    private fun srcMapAyikla(html: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        Regex("""srcMap\s*=\s*\{([^}]*)\}""").find(html)?.groupValues?.get(1)
            ?.let { Regex("""["']?([0-9A-Za-z]+)["']?\s*:\s*["']([^"']+)["']""").findAll(it) }
            ?.forEach { m -> out[m.groupValues[1]] = m.groupValues[2] }
        return out
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("BLX", "data » $data")

        val istenenId = data.substringAfter("?bolum=", "").substringBefore("&")
        val sayfaUrl  = data.substringBefore("?")

        val pageText = app.get(sayfaUrl, interceptor = interceptor).text

        // * yeni tema: diziGetir kayitlarindan 3 adet kaynak slotu uret
        val srcMap   = srcMapAyikla(pageText)
        val kayitlar = diziGetirAyikla(pageText)
        val secili   = kayitlar.firstOrNull { it.id == istenenId } ?: kayitlar.firstOrNull()

        if (secili != null) {
            for ((index, ic) in secili.ic.withIndex()) {
                if (ic.isBlank()) continue
                val dosya   = srcMap[ic] ?: "default"
                val slotUrl = "${mainUrl}/video/data/${dosya}.php?id=${secili.id}&sira=${index + 1}"
                Log.d("BLX", "slot » $slotUrl")
                val resp = app.get(slotUrl, referer = sayfaUrl, interceptor = interceptor)
                cevapIsle(resp.text, sayfaUrl, subtitleCallback, callback)
            }
        } else {
            // * eski yapi: sayfadaki kacisli iframe adresleri
            Regex("""<iframe\s+[^>]*src=\\"([^\\"']+)\\"""").findAll(pageText).forEach { m ->
                val resp = app.get(m.groupValues[1], referer = sayfaUrl, interceptor = interceptor)
                cevapIsle(resp.text, m.groupValues[1], subtitleCallback, callback)
            }
        }

        return true
    }

    /** Bir kaynak cevabindaki jwplayer file/label ya da iframe video adreslerini uretir */
    private suspend fun cevapIsle(
        metin: String, referer: String, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit
    ) {
        var buldu = false

        Regex("""file:"([^"]+)", label: "([^"]+)""").findAll(metin).forEach { m ->
            var videoUrl = m.groupValues[1].trim()
            if (videoUrl.isBlank()) return@forEach
            if (videoUrl.startsWith("//")) videoUrl = "https:$videoUrl"

            var quality = m.groupValues[2]
            var kaynak  = this.name
            if (quality == "FULL") { quality = "1080p"; kaynak = "Google" }
            buldu = true
            Log.d("BLX", "video » $videoUrl  [$quality]")

            callback.invoke(
                newExtractorLink(
                    source = kaynak,
                    name   = kaynak,
                    url    = videoUrl
                ) {
                    this.referer = referer
                    this.quality = getQualityFromName(quality)
                }
            )
        }

        if (buldu) return

        val iframe = Regex("""<iframe[^>]*src=["']([^"']+)["']""").find(metin)?.groupValues?.get(1)?.trim()
            ?: Regex("""src=\\"([^"\\]+)\\"""").find(metin)?.groupValues?.get(1)?.trim()
        if (iframe.isNullOrBlank()) return

        var url = iframe.replace("\\/", "/")
        if (url.startsWith("//")) url = "https:$url"
        Log.d("BLX", "iframe » $url")

        loadExtractor(url, "${mainUrl}/", subtitleCallback, callback)
    }
}
