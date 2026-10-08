package com.bilhan50

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class JetFilmizlePlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(JetFilmizle())
        registerExtractorAPI(PixelDrain())
        registerExtractorAPI(Odnoklassniki())
        registerExtractorAPI(OkRuSSL())
        registerExtractorAPI(OkRuHTTP())
    }
}