package me.roitgrund.bandcampfeed

import BandcampClient
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.http.*
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

private val INTERMEDIATE_RELEASE =
    BandcampReleaseIntermediate(
        "4188283577",
        Url("https://danzanativa.bandcamp.com/album/a-vision-of-light"),
        "A Vision Of Light",
        "Forest On Stasys",
        "danzanativa")

private val JSON = Json { ignoreUnknownKeys = true }
private val HTTP_CLIENT =
    HttpClient(CIO) { install(JsonFeature) { serializer = KotlinxSerializer(JSON) } }

internal class BandcampClientTest {
  @Test
  fun getReleases() {
    runBlocking {
      assertEquals(
          INTERMEDIATE_RELEASE, BandcampClient(JSON, HTTP_CLIENT).getReleases("danzanativa").last())
    }
  }

  @Test
  fun getRelease() {
    runBlocking {
      assertEquals(
          BandcampRelease(
              "4188283577",
              Url("https://danzanativa.bandcamp.com/album/a-vision-of-light"),
              "A Vision Of Light",
              "Forest On Stasys",
              LocalDate.of(2019, 2, 10),
              "danzanativa"),
          BandcampClient(JSON, HTTP_CLIENT).getRelease(INTERMEDIATE_RELEASE))
    }
  }

  @Test
  fun getArtistsAndLabels() {
    runBlocking {
      assertTrue {
        BandcampClient(JSON, HTTP_CLIENT)
            .getArtistsAndLabels("jroitgrund")
            .contains(BandcampPrefix("koseifukuda", "Kosei Fukuda"))
      }
    }
  }
}
