// ! Bu araç @bilhan50 tarafından | @bilhan50 için yazılmıştır.

package com.bilhan50

import com.fasterxml.jackson.annotation.JsonProperty

data class KoreaSearch(
    @JsonProperty("theme") val theme: String
)