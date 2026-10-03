// ! Bu araç @bilhan50 tarafından | @bilhan50 için yazılmıştır.

package com.bilhan50

import com.fasterxml.jackson.annotation.JsonProperty


data class AjaxSource(
    @JsonProperty("status")      val status: String,
    @JsonProperty("iframe")      val iframe: String,
    @JsonProperty("alternative") val alternative: String,
)