// ! Bu araç @bilhan50 tarafından | @bilhan50 için yazılmıştır.

package com.bilhan50

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup

class AnimeciX : MainAPI() {
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
    override var mainUrl              = "https://animecix.tv"
    override var name                 = "AnimeciX"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Anime, TvType.TvSeries, TvType.Movie)

    override var sequentialMainPage = true        // * https://recloudstream.github.io/dokka/-cloudstream/com.lagradost.cloudstream3/-main-a-p-i/index.html#-2049735995%2FProperties%2F101969414
    override var sequentialMainPageDelay       = 200L  // ? 0.20 saniye
    override var sequentialMainPageScrollDelay = 200L  // ? 0.20 saniye

    override val mainPage = mainPageOf(
        "${mainUrl}/secure/titles?type=series&onlyStreamable=true" to "Seriler",
        "${mainUrl}/secure/titles?type=movie&onlyStreamable=true"  to "Filmler",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = app.get(
            "${request.data}&page=${page}&perPage=16",
            headers = mapOf(
                "x-e-h" to "7Y2ozlO+QysR5w9Q6Tupmtvl9jJp7ThFH8SB+Lo7NvZjgjqRSqOgcT2v4ISM9sP10LmnlYI8WQ==.xrlyOBFS5BHjQ2Lk"
            ), interceptor = interceptor
        ).parsedSafe<Category>()

        val home     = response?.pagination?.data?.map { anime ->
            newAnimeSearchResponse(
                anime.title,
                "${mainUrl}/secure/titles/${anime.id}?titleId=${anime.id}",
                TvType.Anime
            ) {
                this.posterUrl = fixUrlNull(anime.poster)
            }
        } ?: listOf<SearchResponse>()

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("${mainUrl}/secure/search/${query}?limit=20", interceptor = interceptor).parsedSafe<Search>() ?: return listOf()

        return response.results.map { anime ->
            newAnimeSearchResponse(
                anime.title,
                "${mainUrl}/secure/titles/${anime.id}?titleId=${anime.id}",
                TvType.Anime
            ) {
                this.posterUrl = fixUrlNull(anime.poster)
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(
            url,
            headers = mapOf(
                "x-e-h" to "7Y2ozlO+QysR5w9Q6Tupmtvl9jJp7ThFH8SB+Lo7NvZjgjqRSqOgcT2v4ISM9sP10LmnlYI8WQ==.xrlyOBFS5BHjQ2Lk"
            ), interceptor = interceptor
        ).parsedSafe<Title>() ?: return null
        val episodes = mutableListOf<Episode>()
        val titleId  = url.substringAfter("?titleId=")

        if (response.title.titleType == "anime") {
            for (sezon in response.title.seasons) {
                val sezonResponse = app.get("${mainUrl}/secure/related-videos?episode=1&season=${sezon.number}&videoId=0&titleId=${titleId}", interceptor = interceptor).parsedSafe<TitleVideos>() ?: return null
                for (video in sezonResponse.videos) {
                    episodes.add(newEpisode(video.url) {
                        this.name = "${video.seasonNum}. Sezon ${video.episodeNum}. Bölüm"
                        this.season = video.seasonNum
                        this.episode = video.episodeNum
                    })
                }
            }
        } else {
            if (response.title.videos.isNotEmpty()) {
                episodes.add(newEpisode(response.title.videos.first().url) {
                    this.name    = "Filmi İzle"
                    this.season  = 1
                    this.episode = 1
                })
            }
        }


        return newTvSeriesLoadResponse(
            response.title.title,
            "${mainUrl}/secure/titles/${response.title.id}?titleId=${response.title.id}",
            TvType.Anime,
            episodes
        ) {
            this.posterUrl = fixUrlNull(response.title.poster)
            this.year      = response.title.year
            this.plot      = response.title.description
            this.tags      = response.title.tags.map { it.name }
            this.score    = response.title.rating.toScore()
            addActors(response.title.actors.map { Actor(it.name, fixUrlNull(it.poster)) })
            addTrailer(response.title.trailer)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("ACX", "data » $data")
        val iframeLink = app.get("${mainUrl}/${data}", referer="${mainUrl}/", interceptor = interceptor).url
        Log.d("ACX", "iframeLink » $iframeLink")

        loadExtractor(iframeLink, "${mainUrl}/", subtitleCallback, callback)

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
