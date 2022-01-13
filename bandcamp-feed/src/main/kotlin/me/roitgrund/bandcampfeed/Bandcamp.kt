package me.roitgrund.bandcampfeed

import io.ktor.http.*
import java.time.LocalDate
import kotlinx.serialization.Serializable

data class BandcampRelease(
    val id: String,
    val url: Url,
    val title: String,
    val artist: String,
    val date: LocalDate,
    val prefix: String
)

data class BandcampReleaseIntermediate(
    val id: String,
    val url: Url,
    val title: String,
    val artist: String,
    val prefix: String
)

@Serializable data class BandcampPrefix(val bandcampPrefix: String, val name: String)
