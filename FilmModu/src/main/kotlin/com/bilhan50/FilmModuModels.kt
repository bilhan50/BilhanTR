// ! Bu araç @bilhan50 tarafından | @bilhan50 için yazılmıştır.

package com.bilhan50

import com.fasterxml.jackson.annotation.JsonProperty


data class GetSource(
    @JsonProperty("subtitle") val subtitle: String?       = null,
    @JsonProperty("sources")  val sources: List<Sources>? = arrayListOf()
)

data class Sources(
    @JsonProperty("src")   val src: String,
    @JsonProperty("label") val label: String,
)