package me.roitgrund.bandcampfeed

import BandcampClient
import io.ktor.http.*
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

internal class BandcampFeedServerKtTest {
  @Test
  fun testEte() {
    runBlocking {
      val bandcampClient = BandcampClient()
      val storage: Storage = InMemoryStorage()

      val feedId =
          createFeed(
              storage, setOf(BandcampPrefix("romancemoderne"), BandcampPrefix("augurirecords")))

      assertEquals(
          listOf(
              BandcampRelease(
                  "2072740262",
                  Url("https://augurirecords.bandcamp.com/album/intensive-care-vol-1"),
                  "Intensive Care, Vol. 1",
                  "Various",
                  LocalDate.parse("2020-04-01")),
              BandcampRelease(
                  "3271455394",
                  Url("https://romancemoderne.bandcamp.com/album/lovers-revenge"),
                  "Lovers Revenge",
                  "LOVERS REVENGE",
                  LocalDate.parse("2015-01-22"))),
          getFeed(feedId, storage, bandcampClient)
              .dropWhile { it.date.isAfter(LocalDate.parse("2020-04-01")) }
              .take(2))
    }
  }
}
