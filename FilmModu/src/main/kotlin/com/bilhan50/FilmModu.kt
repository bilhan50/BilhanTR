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

class FilmModu : MainAPI() {
    override var mainUrl              = "https://www.filmmodu.one"
    override var name                 = "FilmModu"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie)

    // ! CloudFlare bot korumasini asma (DiziBox/WebteIzle ile ayni kalip)
    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor      by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            val doc      = Jsoup.parse(response.peekBody(1024 * 1024).string())

            // Cloudflare "Just a moment..." challenge sayfasi donduyse webview ile as
            if (doc.text().contains("Just a moment")) {
                return cloudflareKiller.intercept(chain)
            }

            return response
        }
    }

    override val mainPage = mainPageOf(
        "${mainUrl}/hd-film-kategori/4k-film-izle"          to "4K",
        "${mainUrl}/hd-film-kategori/aile-filmleri"         to "Aile",
        "${mainUrl}/hd-film-kategori/aksiyon"               to "Aksiyon",
        "${mainUrl}/hd-film-kategori/animasyon"             to "Animasyon",
        "${mainUrl}/hd-film-kategori/belgeseller"           to "Belgesel",
        "${mainUrl}/hd-film-kategori/bilim-kurgu-filmleri"  to "Bilim-Kurgu",
        "${mainUrl}/hd-film-kategori/dram-filmleri"         to "Dram",
        "${mainUrl}/hd-film-kategori/fantastik-filmler"     to "Fantastik",
        "${mainUrl}/hd-film-kategori/gerilim"               to "Gerilim",
        "${mainUrl}/hd-film-kategori/gizem-filmleri"        to "Gizem",
        "${mainUrl}/hd-film-kategori/hd-hint-filmleri"      to "Hint Filmleri",
        "${mainUrl}/hd-film-kategori/kisa-film"             to "Kısa Film",
        "${mainUrl}/hd-film-kategori/hd-komedi-filmleri"    to "Komedi",
        "${mainUrl}/hd-film-kategori/komedi"                to "Komedi",
        "${mainUrl}/hd-film-kategori/korku-filmleri"        to "Korku",
        "${mainUrl}/hd-film-kategori/kult-filmler-izle"     to "Kült Filmler",
        "${mainUrl}/hd-film-kategori/macera-filmleri"       to "Macera",
        "${mainUrl}/hd-film-kategori/muzik"                 to "Müzik",
        "${mainUrl}/hd-film-kategori/odullu-filmler-izle"   to "Oscar Ödüllü Filmler",
        "${mainUrl}/hd-film-kategori/romantik-filmler"      to "Romantik",
        "${mainUrl}/hd-film-kategori/savas"                 to "Savaş",
        "${mainUrl}/hd-film-kategori/savas-filmleri"        to "Savaş",
        "${mainUrl}/hd-film-kategori/stand-up"              to "Stand Up",
        "${mainUrl}/hd-film-kategori/suc-filmleri"          to "Suç",
        "${mainUrl}/hd-film-kategori/tarih"                 to "Tarih",
        "${mainUrl}/hd-film-kategori/tavsiye-filmler"       to "Tavsiye Filmler",
        "${mainUrl}/hd-film-kategori/tv-film"               to "TV film",
        "${mainUrl}/hd-film-kategori/vahsi-bati-filmleri"   to "Vahşi Batı",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}?page=${page}", referer = "$mainUrl/", interceptor = interceptor).document
        val home     = document.select("div.movie").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("a")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null

        // * Afisler lazy-load: gorsel <img src="data:..."> yerine gercek adresi data-src icinde tutar.
        val img       = this.selectFirst("picture img")
        val posterUrl = (img?.attr("data-src")?.takeIf { it.isNotBlank() && !it.startsWith("data:") }
            ?: img?.attr("src")?.takeIf { it.isNotBlank() && !it.startsWith("data:") })
            ?.let { fixUrlNull(it) }

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/film-ara?term=${query}", referer = "$mainUrl/", interceptor = interceptor).document

        return document.select("div.movie").mapNotNull { it.toMainPageResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val detay    = app.get(url, referer = "$mainUrl/", interceptor = interceptor)
        val document = detay.document

        val orgTitle    = document.selectFirst("div.titles h1")?.text()?.trim() ?: return null
        val altTitle    = document.selectFirst("div.titles h2")?.text()?.trim() ?: ""
        val title       = if (altTitle.isNotEmpty()) "$orgTitle - $altTitle" else orgTitle
        // * yeni tema: poster <img itemprop="image">, film bilgileri "Tur"/"IMDB" etiketli <p> icinde
        val poster      = fixUrlNull(document.selectFirst("img[itemprop='image']")?.attr("src"))
            ?: fixUrlNull(document.selectFirst("img.img-responsive")?.attr("src"))
        val description = document.selectFirst("p[itemprop='description']")?.text()?.trim()
        val year        = document.selectFirst("span[itemprop='dateCreated']")?.text()?.trim()?.toIntOrNull()

        // * tur linkleri artik /film-tur/ seklinde (eski: -kategori/); <p> icindekiler filmin kendi turu
        var tags = document.select("p > a[href*='/film-tur/']").map { it.text().trim() }.filter { it.isNotEmpty() }
        if (tags.isEmpty()) {
            tags = document.select("div.description a[href*='-kategori/']").map { it.text() }
        }

        val rating = document.select("p").firstOrNull { p ->
            p.selectFirst("strong")?.text()?.contains("IMDB", ignoreCase = true) == true
        }?.ownText()?.substringAfter(":")?.trim()?.toDoubleOrNull()
            ?: document.selectFirst("div.description p")?.ownText()?.split(" ")?.last()?.trim()?.toDoubleOrNull()

        var actors = document.select("a[itemprop='actor'] span[itemprop='name']")
            .map { Actor(it.text().trim()) }.filter { it.name.isNotBlank() }
        if (actors.isEmpty()) {
            actors = document.select("div.description a[href*='-oyuncu-']").mapNotNull {
                val ad = it.selectFirst("span")?.text()?.trim() ?: return@mapNotNull null
                Actor(ad)
            }
        }
        actors = actors.distinctBy { it.name }
        val trailer     = document.selectFirst("div.container iframe")?.attr("src")

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot      = description
            this.year      = year
            this.tags      = tags
            this.score     = rating?.let { Score.from10(it) }
            addActors(actors)
            addTrailer(trailer)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("FLMMD", "data » $data")

        val detay     = app.get(data, referer = "$mainUrl/", interceptor = interceptor)
        val document  = detay.document
        // * Ham HTML aliniyor: videoId/videoType <script> icinde ve Jsoup text() script icerigini dislar.
        val detayHtml = document.toString()

        // * 1) Alternatif oynatici linkleri (Fragman haric)
        val alternatifler = document.select("div.alternates a").mapNotNull {
            val link = fixUrlNull(it.attr("href")) ?: return@mapNotNull null
            val ad   = it.text().trim()
            if (ad.isEmpty() || ad.equals("Fragman", ignoreCase = true)) null else (ad to link)
        }

        // * (etiket, movie_id, tip) seklinde cagri listesi
        val istekler = ArrayList<Triple<String, String, String>>()

        for ((ad, link) in alternatifler) {
            val altHtml = try { app.get(link, referer = data, interceptor = interceptor).document.toString() } catch (e: Throwable) { "" }
            val vidId   = Regex("""var videoId\s*=\s*'([^']*)'""").find(altHtml)?.groupValues?.get(1)
            if (vidId.isNullOrEmpty()) continue

            val vidType = Regex("""var videoType\s*=\s*'([^']*)'""").find(altHtml)?.groupValues?.get(1)
            if (!vidType.isNullOrEmpty()) {
                istekler.add(Triple(ad, vidId, vidType))
            } else {
                // * tip bosssa site tr (dublaj) / en (altyazi) ikilisini kullaniyor
                istekler.add(Triple("$ad Türkçe Dublaj", vidId, "tr"))
                istekler.add(Triple("$ad Türkçe Altyazılı", vidId, "en"))
            }
        }

        // * 2) Yedek: alternatif hic bulunamazsa detay sayfasinin kendi videoId'si ile tr/en dene
        if (istekler.isEmpty()) {
            val detayId = Regex("""var videoId\s*=\s*'([^']*)'""").find(detayHtml)?.groupValues?.get(1)
            if (!detayId.isNullOrEmpty()) {
                istekler.add(Triple("${this.name} Türkçe Dublaj", detayId, "tr"))
                istekler.add(Triple("${this.name} Türkçe Altyazılı", detayId, "en"))
            }
        }

        var linkSayisi = 0
        for ((etiket, vidId, vidType) in istekler) {
            val kaynak = "${mainUrl}/get-source?movie_id=${vidId}&type=${vidType}"
            val vidReq = try {
                app.get(kaynak, referer = data, interceptor = interceptor).parsedSafe<GetSource>()
            } catch (e: Throwable) { null } ?: continue

            if (vidReq.subtitle != null) {
                subtitleCallback.invoke(
                    SubtitleFile(
                        lang = "Türkçe",
                        url  = fixUrl(vidReq.subtitle)
                    )
                )
            }

            for (source in vidReq.sources ?: emptyList()) {
                linkSayisi++
                callback.invoke(
                    newExtractorLink(
                        source = "${this.name} - $etiket",
                        name = "${this.name} - $etiket",
                        url = fixUrl(source.src)
                    ) {
                        this.referer = "${mainUrl}/"
                        this.quality = getQualityFromName(source.label)
                        // * get-source yaniti application/x-mpegURL dondurur, adreste uzanti yok;
                        // * tip VIDEO olarak yanlis cikarsa oynatma patlar
                        this.type    = ExtractorLinkType.M3U8
                    }
                )
            }
        }

        Log.d("FLMMD", "istek=${istekler.size} link=$linkSayisi")
        return true
    }
}