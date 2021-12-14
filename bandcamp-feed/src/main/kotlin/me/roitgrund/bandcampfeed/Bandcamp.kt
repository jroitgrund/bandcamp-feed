package me.roitgrund.bandcampfeed

import io.ktor.http.*
import java.time.LocalDate

data class BandcampPrefix(val prefix: String)

data class BandcampRelease(
    val id: String,
    val url: Url,
    val title: String,
    val artist: String,
    val date: LocalDate
)

data class BandcampReleaseIntermediate(
    val id: String,
    val url: Url,
    val title: String,
    val artist: String
)
