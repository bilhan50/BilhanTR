version = 5

cloudstream {
    authors     = listOf("bilhan50")
    language    = "tr"
    description = "Türk Anime TV - Türkiye'nin Online Anime izleme sitesi."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Anime", "TvSeries", "Movie")
    iconUrl = "https://www.google.com/s2/favicons?domain=www.turkanime.tv&sz=%size%"
}