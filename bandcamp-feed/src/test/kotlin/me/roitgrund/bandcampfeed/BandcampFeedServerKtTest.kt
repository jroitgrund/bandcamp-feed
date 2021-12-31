package me.roitgrund.bandcampfeed

import BandcampClient
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import org.opentest4j.AssertionFailedError
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

internal class BandcampFeedServerKtTest {

  @Test
  fun testEte(@TempDir tempDir: Path) {
    val dbPath = tempDir.resolve("db.sqlite")
    val dbUrl = "jdbc:sqlite:${dbPath}"
    runBlocking {
      val bandcampClient = BandcampClient()
      val storage: Storage = SqlStorage(dbUrl)

      updatePrefixesInBackground(storage, dbUrl, bandcampClient)

      val feedId =
          storage.saveFeed(
              "title", setOf(BandcampPrefix("romancemoderne"), BandcampPrefix("augurirecords")))

      (0..60).forEach { i ->
        try {
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
              checkNotNull(storage.getFeedReleases(feedId))
                  .second
                  .dropWhile { it.date.isAfter(LocalDate.parse("2020-04-01")) }
                  .take(2))
        } catch (e: AssertionFailedError) {
          if (i == 60) {
            throw e
          }
          delay(1000)
        }
      }
    }
  }
}
