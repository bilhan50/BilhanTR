package com.bilhan50

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Dzen : ExtractorApi(){
    override val name            = "Dzen"
    override val mainUrl         = "https://cdn.dzen.ru/"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val type = if (url.contains(".m3u8")) "m3u8" else if(url.contains(".mpd")) "dash" else null

        val extractorLink = when(type) {
            "m3u8" -> newExtractorLink(
                source = this.name,
                name = this.name,
                url = url
            ) {
                this.referer = ""
                this.quality = Qualities.Unknown.value
                this.type = ExtractorLinkType.M3U8
            }

            "dash" -> newExtractorLink(
                source = this.name,
                name = this.name,
                url = url
            ) {
                this.referer = ""
                this.quality = Qualities.Unknown.value
                this.type = ExtractorLinkType.DASH
            }

            else -> null
        }

        if (extractorLink != null) {
            callback.invoke(extractorLink)
        }
    }
}