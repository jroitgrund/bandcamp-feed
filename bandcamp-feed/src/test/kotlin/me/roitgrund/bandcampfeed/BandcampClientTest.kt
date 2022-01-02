package me.roitgrund.bandcampfeed

import BandcampClient
import io.ktor.http.*
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

private val INTERMEDIATE_RELEASE =
    BandcampReleaseIntermediate(
        "4188283577",
        Url("https://danzanativa.bandcamp.com/album/a-vision-of-light"),
        "A Vision Of Light",
        "Forest On Stasys",
        BandcampPrefix("danzanativa"))

internal class BandcampClientTest {
  @Test
  fun getReleases() {
    runBlocking {
      assertEquals(
          INTERMEDIATE_RELEASE, BandcampClient().getReleases(BandcampPrefix("danzanativa")).last())
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
              BandcampPrefix("danzanativa")),
          BandcampClient().getRelease(INTERMEDIATE_RELEASE))
    }
  }

  @Test
  fun getArtistsAndLabels() {
    runBlocking {
      assertTrue {
        BandcampClient().getArtistsAndLabels("jroitgrund").contains(BandcampPrefix("koseifukuda"))
      }
    }
  }
}
