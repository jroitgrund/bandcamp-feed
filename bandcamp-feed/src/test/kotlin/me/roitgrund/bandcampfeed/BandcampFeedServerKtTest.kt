package me.roitgrund.bandcampfeed

import BandcampClient
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.http.*
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.io.TempDir
import org.opentest4j.AssertionFailedError

private val JSON = Json { ignoreUnknownKeys = true }
private val HTTP_CLIENT =
    HttpClient(CIO) { install(JsonFeature) { serializer = KotlinxSerializer(JSON) } }

internal class BandcampFeedServerKtTest {

  @FlowPreview
  @Test
  fun testEte(@TempDir tempDir: Path) {
    val dbPath = tempDir.resolve("db.sqlite")
    val dbUrl = "jdbc:sqlite:${dbPath}"
    runBlocking {
      val bandcampClient = BandcampClient(JSON, HTTP_CLIENT)
      val storage = SqlStorage(dbUrl)

      Flyway.configure().dataSource(dbUrl, "", "").load().migrate()
      updatePrefixesInBackground(storage, bandcampClient)

      storage.savePrefixes(
          setOf(
              BandcampPrefix("romancemoderne", "Romance Moderne"),
              BandcampPrefix("augurirecords", "Auguri Records")))
      val feedId = storage.saveFeed("title", "me@me.com", setOf("romancemoderne", "augurirecords"))

      (0..60).forEach { i ->
        try {
          val expected =
              listOf(
                  BandcampRelease(
                      "2072740262",
                      Url("https://augurirecords.bandcamp.com/album/intensive-care-vol-1"),
                      "Intensive Care, Vol. 1",
                      "Various",
                      LocalDate.parse("2020-04-01"),
                      "augurirecords"),
                  BandcampRelease(
                      "3271455394",
                      Url("https://romancemoderne.bandcamp.com/album/lovers-revenge"),
                      "Lovers Revenge",
                      "LOVERS REVENGE",
                      LocalDate.parse("2015-01-22"),
                      "romancemoderne"))

          val releases = getReleasesPaginated(storage, feedId)
          assertEquals(
              expected,
              releases.dropWhile { it.date.isAfter(LocalDate.parse("2020-04-01")) }.take(2))

          assertEquals(
              expected,
              checkNotNull(storage.getFeedReleases(feedId, null, null, null))
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

  private suspend fun getReleasesPaginated(
      storage: SqlStorage,
      feedId: String
  ): MutableList<BandcampRelease> {
    val releases = mutableListOf<BandcampRelease>()
    var curr: Pair<String, List<BandcampRelease>>? = null
    while (curr == null || curr.second.isNotEmpty()) {
      val next =
          checkNotNull(
              storage.getFeedReleases(
                  feedId, curr?.second?.last()?.id, curr?.second?.last()?.date?.toString(), 1))
      releases.addAll(next.second)
      curr = next
    }
    return releases
  }
}
