// ! Bu araç @bilhan50 tarafından | @bilhan50 için yazılmıştır.

package com.bilhan50

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.fasterxml.jackson.annotation.JsonProperty
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup

class JetFilmizle : MainAPI() {
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
    override var mainUrl              = "https://jetfilmizle.now"
    override var name                 = "JetFilmizle"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "${mainUrl}/?page="                                     to "Son Filmler",
        "${mainUrl}/netflix/?page="                             to "Netflix",
        "${mainUrl}/editorun-secimi/?page="                     to "Editörün Seçimi",
        "${mainUrl}/turk-film-izle/?page="                      to "Türk Filmleri",
        "${mainUrl}/cizgi-filmler-izle/?page="                  to "Çizgi Filmler",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}${page}", interceptor = interceptor).document
        val home     = document.select("div.film-card, article.movie").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        var title = this.selectFirst("h2 a")?.text() ?: this.selectFirst("h3 a")?.text() ?: this.selectFirst("h4 a")?.text() ?: this.selectFirst("h5 a")?.text() ?: this.selectFirst("h6 a")?.text() ?: return null
        title = title.substringBefore(" izle")

        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        var posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))
        if (posterUrl == null) {
            posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        }

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // * yeni arama ucu: GET /arama-json?q=...  (eski POST /filmara.php artik 404 donuyor)
        val response = app.get(
            "${mainUrl}/arama-json?q=${query}",
            referer     = "${mainUrl}/",
            headers     = mapOf("Accept" to "application/json", "X-Requested-With" to "XMLHttpRequest"),
            interceptor = interceptor
        ).parsedSafe<JetArama>() ?: return listOf()

        return response.results.orEmpty().mapNotNull { r ->
            val href      = fixUrlNull(r.url) ?: return@mapNotNull null
            val title     = r.title?.trim()?.substringBefore(" izle") ?: return@mapNotNull null
            val posterUrl = fixUrlNull(r.poster)

            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        }
    }

    data class JetArama(
        @JsonProperty("results") val results: List<JetAramaOge>?
    )

    data class JetAramaOge(
        @JsonProperty("title")  val title: String?,
        @JsonProperty("url")    val url: String?,
        @JsonProperty("poster") val poster: String?,
        @JsonProperty("type")   val type: String?
    )

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, interceptor = interceptor).document

        // * yeni tema: h1.film-title (orijinal ad span icinde)  |  eski tema: section.movie-exp
        val rawTitle = document.selectFirst("h1.film-title")?.ownText()?.trim()?.ifBlank { null }
            ?: document.selectFirst("h1.film-title")?.text()?.trim()
            ?: document.selectFirst("section.movie-exp div.movie-exp-title")?.text()?.substringBefore(" izle")?.trim()
            ?: return null
        val title = Regex("""\s*\([^)]*\)${'$'}""").replace(rawTitle, "").trim().ifBlank { rawTitle }

        val poster = fixUrlNull(document.selectFirst("img.film-poster")?.attr("src"))
            ?: fixUrlNull(document.selectFirst("section.movie-exp img")?.attr("data-src"))
            ?: fixUrlNull(document.selectFirst("section.movie-exp img")?.attr("src"))
            ?: fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))

        // * yeni temada film bilgileri #active-player data-* ozelliklerinde
        val player    = document.selectFirst("#active-player")
        val year      = player?.attr("data-film-year")?.toIntOrNull()
            ?: Regex("""(\d{4})""").find(document.selectXpath("//div[@class='yap' and contains(strong, 'Vizyon') or contains(strong, 'Yapım')]").text())?.groupValues?.get(1)?.toIntOrNull()
        val duration  = player?.attr("data-film-duration")?.toIntOrNull()
        val rating    = player?.attr("data-film-rating")?.toScore()
            ?: document.selectFirst("section.movie-exp div.imdb_puan span")?.text()?.split(" ")?.last()?.toScore()

        val description = document.selectFirst("div.film-description p")?.text()?.trim()
            ?: document.selectFirst("div.film-description")?.text()?.trim()
            ?: document.selectFirst("section.movie-exp p.aciklama")?.text()?.trim()

        var tags = document.select("a.category-badge-details").map { it.text().trim() }.filter { it.isNotEmpty() }
        if (tags.isEmpty()) {
            tags = document.select("section.movie-exp div.catss a").map { it.text().trim() }.filter { it.isNotEmpty() }
        }

        var actors = document.select("a[href*='/oyuncu/']").map { Actor(it.text().trim()) }
            .filter { it.name.isNotBlank() }
        if (actors.isEmpty()) {
            // * oyuncular JSON-LD icinde
            val ld = Regex("""(?s)"actor"\s*:\s*\[(.*?)\]""").find(document.html())?.groupValues?.get(1)
            if (ld != null) {
                actors = Regex(""""name"\s*:\s*"([^"]+)"""").findAll(ld).map { Actor(it.groupValues[1]) }.toList()
            }
        }
        actors = actors.distinctBy { it.name }

        val recommendations = document.select("div.film-card, article.movie").mapNotNull { it.toSearchResult() }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl       = poster
            this.year            = year
            this.plot            = description
            this.tags            = tags
            this.duration        = duration
            this.score           = rating
            this.recommendations = recommendations
            addActors(actors)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("JTF", "data » $data")
        val document = app.get(data, interceptor = interceptor).document

        // * yeni tema: filmId gizli formda, kaynaklar .player-source-btn butonlarinda
        //   butonlar POST /jetplayer ile oynatilir -> yanitta <iframe src="...">
        val filmId   = document.selectFirst("input[name=film_id]")?.attr("value")
        val kaynaklar = document.select("button.player-source-btn").mapNotNull { b ->
            val index = b.attr("data-source-index").ifBlank { return@mapNotNull null }
            val tip   = b.attr("data-player-type").ifBlank { "dublaj" }
            index to tip
        }.distinct()

        Log.d("JTF", "filmId » $filmId  kaynak » ${kaynaklar.size}")

        val iframes = mutableSetOf<String>()

        if (!filmId.isNullOrBlank()) {
            for ((index, tip) in kaynaklar) {
                val yanit = app.post(
                    "${mainUrl}/jetplayer",
                    referer     = data,
                    data        = mapOf("film_id" to filmId, "source_index" to index, "player_type" to tip),
                    interceptor = interceptor
                ).text

                Regex("""<iframe[^>]*src=["']([^"']+)["']""").findAll(yanit).forEach { m ->
                    val src = m.groupValues[1].trim()
                    if (src.isBlank() || src.startsWith("javascript:")) return@forEach
                    iframes.add(src)
                }
            }
        }

        // * eski yapi: div#movie + film_part baglantilari
        if (iframes.isEmpty()) {
            val mainIframe = fixUrlNull(document.selectFirst("div#movie iframe")?.attr("data-src")) ?: fixUrlNull(document.selectFirst("div#movie iframe")?.attr("data")) ?: fixUrlNull(document.selectFirst("div#movie iframe")?.attr("src"))
            Log.d("JTF", "mainIframe » $mainIframe")
            if (mainIframe != null) {
                iframes.add(mainIframe)
            }

            document.select("div.film_part a").forEach {
                val source = it.selectFirst("span")?.text()?.trim() ?: return@forEach
                if (source.lowercase().contains("fragman")) return@forEach

                val movDoc = app.get(it.attr("href"), interceptor = interceptor).document
                val iframe = fixUrlNull(movDoc.selectFirst("div#movie iframe")?.attr("data-src")) ?: fixUrlNull(movDoc.selectFirst("div#movie iframe")?.attr("data")) ?: fixUrlNull(movDoc.selectFirst("div#movie iframe")?.attr("src"))
                Log.d("JTF", "iframe » $iframe")

                if (iframe != null) {
                    iframes.add(iframe)
                } else {
                    movDoc.select("div#movie p a").forEach downloadLinkForEach@{ link ->
                        val downloadLink = fixUrlNull(link.attr("href")) ?: return@downloadLinkForEach
                        iframes.add(downloadLink)
                    }
                }
            }
        }

        for (iframe in iframes) {
            val url = when {
                iframe.startsWith("//") -> "https:$iframe"
                iframe.startsWith("/")  -> fixUrlNull(iframe) ?: continue
                else                    -> iframe
            }
            Log.d("JTF", "iframe » $url")
            when {
                url.contains("videopark") -> videoparkCoz(url, callback)
                url.contains("vidmoly")   -> vidmolyCoz(url, callback)
                url.contains("jetv.xyz")  -> {
                    val jetvDoc    = app.get(url, interceptor = interceptor).document
                    val jetvIframe = fixUrlNull(jetvDoc.selectFirst("iframe")?.attr("src")) ?: continue
                    Log.d("JTF", "jetvIframe » $jetvIframe")

                    loadExtractor(jetvIframe, "${mainUrl}/", subtitleCallback, callback)
                }
                else -> loadExtractor(url, "${mainUrl}/", subtitleCallback, callback)
            }
        }

        return true
    }

    /**
     * videopark.top oynaticisi JWPlayer'i Remote API'den besler.
     * Sayfadaki WORKER_URL + PUB_ID + PUBLISHER_ID degerleriyle
     * /api/stream ucundan HLS adresi alinir.
     */
    private suspend fun videoparkCoz(url: String, callback: (ExtractorLink) -> Unit) {
        val kaynak = app.get(url, referer = "${mainUrl}/", interceptor = interceptor).text

        val worker    = Regex("""WORKER_URL\s*=\s*['"]([^'"]+)['"]""").find(kaynak)?.groupValues?.get(1) ?: return
        val pubId     = Regex("""PUB_ID\s*=\s*["']([^"']*)["']""").find(kaynak)?.groupValues?.get(1)
        val publisher = Regex("""PUBLISHER_ID\s*=\s*["']([^"']*)["']""").find(kaynak)?.groupValues?.get(1)
        val videoId   = Regex("""VIDEO_ID\s*=\s*["']([^"']*)["']""").find(kaynak)?.groupValues?.get(1)

        val apiUrl = when {
            !pubId.isNullOrBlank() -> buildString {
                append(worker).append("/api/stream?pubId=").append(pubId)
                if (!publisher.isNullOrBlank()) { append("&publisherId=").append(publisher) }
            }
            !videoId.isNullOrBlank() -> "$worker/api/video?id=$videoId"
            else -> return
        }

        val json = app.get(apiUrl, referer = url, interceptor = interceptor).text
        val m3u  = Regex(""""hlsSource"\s*:\s*\{\s*"file"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)
            ?: Regex(""""file"\s*:\s*"(https?://[^"]+\.m3u8[^"]*)"""").find(json)?.groupValues?.get(1)
        if (m3u.isNullOrBlank()) return

        val referer = Regex("""^https?://[^/]+""").find(url)?.value?.plus("/") ?: "https://videopark.top/"
        Log.d("JTF", "videopark » $m3u")

        callback.invoke(
            newExtractorLink(source = "VideoPark", name = "VideoPark", url = m3u) {
                this.referer = referer
                this.quality = Qualities.Unknown.value
                this.type    = ExtractorLinkType.M3U8
            }
        )
    }

    /** vidmoly embed sayfasi JWPlayer kaynagini dogrudan verir (file: '...master.m3u8?...') */
    private suspend fun vidmolyCoz(url: String, callback: (ExtractorLink) -> Unit) {
        val headers = mapOf(
            "User-Agent"     to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36",
            "Sec-Fetch-Dest" to "iframe"
        )
        val kaynak = app.get(url, headers = headers, referer = "${mainUrl}/", interceptor = interceptor).text
        val m3u    = Regex("""https?://[^"'\s\\]+\.m3u8[^"'\s\\]*""").find(kaynak)?.value
            ?: Regex("""file:\s*["']([^"']*\.m3u8[^"']*)["']""").find(kaynak)?.groupValues?.get(1)
        if (m3u.isNullOrBlank()) return

        Log.d("JTF", "vidmoly » $m3u")

        callback.invoke(
            newExtractorLink(source = "VidMoly", name = "VidMoly", url = m3u) {
                this.referer = "https://vidmoly.to/"
                this.quality = Qualities.Unknown.value
                this.type    = ExtractorLinkType.M3U8
            }
        )
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
