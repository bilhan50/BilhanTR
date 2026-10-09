version = 1

cloudstream {
    authors     = listOf("bilhan50")
    language    = "tr"
    description = "DiziRoll - yabanci dizileri turkce dublaj veya altyazili olarak 1080p HD izleyin."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("TvSeries")
    iconUrl = "https://www.google.com/s2/favicons?domain=diziroll.club&sz=%size%"
}
