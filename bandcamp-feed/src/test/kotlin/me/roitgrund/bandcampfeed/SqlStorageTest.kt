package me.roitgrund.bandcampfeed

import java.nio.file.Path
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

internal class SqlStorageTest {

  @Test
  fun testEte(@TempDir tempDir: Path) {
    val dbPath = tempDir.resolve("db.sqlite")
    val dbUrl = "jdbc:sqlite:${dbPath}"
    runBlocking {
      val storage = SqlStorage(dbUrl)

      Flyway.configure().dataSource(dbUrl, "", "").load().migrate()

      assertEquals(listOf(), storage.getUserFeeds("me@me.com"))

      storage.savePrefixes(
          setOf(
              BandcampPrefix("danzanativa", "Danza Nativa"),
              BandcampPrefix("romancemoderne", "Romance Moderne"),
              BandcampPrefix("augurirecords", "Auguri Records"),
              BandcampPrefix("haws", "Haws")))

      assertEquals(null, storage.getNextPrefix(null))

      val feedId = storage.saveFeed("title", "me@me.com", setOf("romancemoderne", "augurirecords"))

      assertEquals("augurirecords", storage.getNextPrefix(null))
      assertEquals("romancemoderne", storage.getNextPrefix("augurirecords"))
      assertEquals("augurirecords", storage.getNextPrefix("romancemoderne"))

      assertEquals(
          listOf(
              UserFeed(
                  feedId,
                  "title",
                  setOf(
                      BandcampPrefix("romancemoderne", "Romance Moderne"),
                      BandcampPrefix("augurirecords", "Auguri Records")))),
          storage.getUserFeeds("me@me.com"))

      assert(!storage.editFeed(feedId, "other-title", "not-me@me.com", setOf()))

      assert(storage.editFeed(feedId, "other-title", "me@me.com", setOf("haws")))

      assertEquals("haws", storage.getNextPrefix("romancemoderne"))
      assertEquals("haws", storage.getNextPrefix("haws"))

      assertEquals(
          listOf(UserFeed(feedId, "other-title", setOf(BandcampPrefix("haws", "Haws")))),
          storage.getUserFeeds("me@me.com"))
    }
  }
}
