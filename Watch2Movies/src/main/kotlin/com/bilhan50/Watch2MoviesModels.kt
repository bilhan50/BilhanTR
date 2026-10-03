// ! Bu araç @bilhan50 tarafından | @bilhan50 için yazılmıştır.

package com.bilhan50

import com.fasterxml.jackson.annotation.JsonProperty


@Suppress("unused")
data class Sources(
    @JsonProperty("type") val type: String,
    @JsonProperty("link") val link: String,
    @JsonProperty("sources") val sources: List<String?>,
    @JsonProperty("tracks") val tracks: List<String?>,
    @JsonProperty("title") val title: String
)