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

      val feedId = storage.saveFeed("title", "me@me.com", setOf("romancemoderne", "augurirecords"))

      assertEquals(
          listOf(UserFeed(feedId, "title", setOf("romancemoderne", "augurirecords"))),
          storage.getUserFeeds("me@me.com"))

      assert(!storage.editFeed(feedId, "other-title", "not-me@me.com", setOf()))

      assert(storage.editFeed(feedId, "other-title", "me@me.com", setOf("haws")))

      assertEquals(
          listOf(UserFeed(feedId, "other-title", setOf("haws"))), storage.getUserFeeds("me@me.com"))
    }
  }
}
