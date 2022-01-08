package me.roitgrund.bandcampfeed

import com.google.common.base.Suppliers
import io.ktor.http.*
import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDate
import java.util.*
import java.util.function.Supplier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.roitgrund.bandcampfeed.sql.tables.*
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.DSL.asterisk
import org.jooq.impl.DSL.inline

data class FeedID(val id: UUID)

data class ReleaseId(val id: String)

class SqlStorage {
  private val mutex = Mutex()
  private val connection: Supplier<Connection>

  constructor(url: String) {
    connection = Suppliers.memoize { DriverManager.getConnection(url) }
  }

  suspend fun saveUser(email: String) {
    runWithConnection { c ->
      val user = c.newRecord(Users.USERS)
      user.userEmail = email
      c.insertInto(Users.USERS).set(user).onDuplicateKeyIgnore().execute()
    }
  }

  suspend fun getUserFeeds(email: String): Map<String, Set<BandcampPrefix>> {
    return runWithConnection { c ->
      c
          .select(Feeds.FEEDS.FEED_NAME, FeedsPrefixes.FEEDS_PREFIXES.BANDCAMP_PREFIX)
          .from(Users.USERS)
          .join(Feeds.FEEDS)
          .on(Users.USERS.USER_EMAIL.eq(Feeds.FEEDS.USER_EMAIL))
          .join(FeedsPrefixes.FEEDS_PREFIXES)
          .on(Feeds.FEEDS.FEED_ID.eq(FeedsPrefixes.FEEDS_PREFIXES.FEED_ID))
          .where(Users.USERS.USER_EMAIL.eq(email))
          .fetchGroups(Feeds.FEEDS.FEED_NAME, FeedsPrefixes.FEEDS_PREFIXES.BANDCAMP_PREFIX)
          .mapValues { record ->
            record.value.asSequence().map { prefix -> BandcampPrefix(prefix) }.toSet()
          }
    }
  }

  suspend fun saveFeed(name: String, email: String, bandcampPrefixes: Set<BandcampPrefix>): FeedID {
    return runWithConnection { c ->
      c.transactionResult { tx ->
        val dsl = DSL.using(tx)
        dsl.loadInto(BandcampPrefixes.BANDCAMP_PREFIXES)
            .batchAll()
            .onDuplicateKeyIgnore()
            .loadRecords(
                bandcampPrefixes.map {
                  val newRecord = dsl.newRecord(BandcampPrefixes.BANDCAMP_PREFIXES)
                  newRecord.bandcampPrefix = it.prefix
                  newRecord
                })
            .fields(BandcampPrefixes.BANDCAMP_PREFIXES.fields().toList())
            .execute()

        val feedId = FeedID(UUID.randomUUID())

        val feed = dsl.newRecord(Feeds.FEEDS)
        feed.feedName = name
        feed.userEmail = email
        feed.feedId = feedId.id.toString()
        feed.store()

        dsl.batchStore(
                bandcampPrefixes.map {
                  val feedPrefix = dsl.newRecord(FeedsPrefixes.FEEDS_PREFIXES)
                  feedPrefix.bandcampPrefix = it.prefix
                  feedPrefix.feedId = feedId.id.toString()
                  feedPrefix
                })
            .execute()

        feedId
      }
    }
  }

  suspend fun editFeed(
      feedId: FeedID,
      name: String,
      email: String,
      bandcampPrefixes: Set<BandcampPrefix>
  ): Boolean {
    return runWithConnection { c ->
      c.transactionResult { tx ->
        val dsl = DSL.using(tx)
        val existingEmail =
            dsl.select(Feeds.FEEDS.USER_EMAIL)
                .from(Feeds.FEEDS)
                .where(Feeds.FEEDS.FEED_ID.eq(feedId.id.toString()))
                .fetchOne()
                ?.component1()
        if (existingEmail == null || email != existingEmail) {
          false
        } else {
          dsl.deleteFrom(FeedsPrefixes.FEEDS_PREFIXES)
              .where(FeedsPrefixes.FEEDS_PREFIXES.FEED_ID.eq(feedId.id.toString()))
              .execute()
          dsl.batchStore(
                  bandcampPrefixes.map {
                    val feedPrefix = dsl.newRecord(FeedsPrefixes.FEEDS_PREFIXES)
                    feedPrefix.bandcampPrefix = it.prefix
                    feedPrefix.feedId = feedId.id.toString()
                    feedPrefix
                  })
              .execute()
          dsl.update(Feeds.FEEDS)
              .set(Feeds.FEEDS.FEED_NAME, name)
              .where(Feeds.FEEDS.FEED_ID.eq(feedId.id.toString()))
              .execute()
          true
        }
      }
    }
  }

  suspend fun deleteFeed(feedId: FeedID) {
    return runWithConnection { c ->
      c.deleteFrom(Feeds.FEEDS).where(Feeds.FEEDS.FEED_ID.eq(feedId.id.toString())).execute()
    }
  }

  suspend fun addRelease(bandcampPrefix: BandcampPrefix, bandcampRelease: BandcampRelease) {
    runWithConnection { c ->
      c.transaction { tx ->
        val dsl = DSL.using(tx)
        val release = dsl.newRecord(Releases.RELEASES)
        release.releaseId = bandcampRelease.id
        release.url = bandcampRelease.url.toString()
        release.title = bandcampRelease.title
        release.artist = bandcampRelease.artist
        release.releaseDate = bandcampRelease.date.toString()
        release.store()

        val joinRecord = dsl.newRecord(ReleasesPrefixes.RELEASES_PREFIXES)
        joinRecord.releaseId = bandcampRelease.id
        joinRecord.bandcampPrefix = bandcampPrefix.prefix
        joinRecord.store()
      }
    }
  }

  suspend fun getFeedReleases(feedId: FeedID): Pair<String, List<BandcampRelease>>? {
    return runWithConnection { c ->
      val releases =
          c.select(asterisk())
              .from(Feeds.FEEDS)
              .join(FeedsPrefixes.FEEDS_PREFIXES)
              .on(Feeds.FEEDS.FEED_ID.eq(FeedsPrefixes.FEEDS_PREFIXES.FEED_ID))
              .join(ReleasesPrefixes.RELEASES_PREFIXES)
              .on(
                  FeedsPrefixes.FEEDS_PREFIXES.BANDCAMP_PREFIX.eq(
                      ReleasesPrefixes.RELEASES_PREFIXES.BANDCAMP_PREFIX))
              .join(Releases.RELEASES)
              .on(ReleasesPrefixes.RELEASES_PREFIXES.RELEASE_ID.eq(Releases.RELEASES.RELEASE_ID))
              .where(Feeds.FEEDS.FEED_ID.eq(feedId.id.toString()))
              .fetch()
              .toList()
      if (releases.isEmpty()) {
        val name =
            c.select(Feeds.FEEDS.FEED_NAME)
                .from(Feeds.FEEDS)
                .where(Feeds.FEEDS.FEED_ID.eq(feedId.id.toString()))
                .fetchOne(Feeds.FEEDS.FEED_NAME)
        if (name != null) {
          (name to emptyList())
        } else {
          null
        }
      } else {
        (releases.first().into(Feeds.FEEDS).feedName to
            releases
                .asSequence()
                .map {
                  val releaseRecord = it.into(Releases.RELEASES)
                  BandcampRelease(
                      releaseRecord.releaseId,
                      Url(releaseRecord.url),
                      releaseRecord.title,
                      releaseRecord.artist,
                      LocalDate.parse(releaseRecord.releaseDate),
                      BandcampPrefix(it.into(ReleasesPrefixes.RELEASES_PREFIXES).bandcampPrefix))
                }
                .sortedByDescending(BandcampRelease::date)
                .toList())
      }
    }
  }

  suspend fun isReleasePresent(releaseId: ReleaseId): Boolean {
    return runWithConnection { c ->
      c.select(inline("1"))
          .from(Releases.RELEASES)
          .where(Releases.RELEASES.RELEASE_ID.eq(releaseId.id))
          .count() > 0
    }
  }

  suspend fun getNextPrefix(prefix: BandcampPrefix?): BandcampPrefix? {
    return runWithConnection { c ->
      val fetchOne =
          c.select(BandcampPrefixes.BANDCAMP_PREFIXES.BANDCAMP_PREFIX)
              .from(BandcampPrefixes.BANDCAMP_PREFIXES)
              .where(
                  BandcampPrefixes.BANDCAMP_PREFIXES.BANDCAMP_PREFIX.greaterThan(
                      prefix?.prefix ?: ""))
              .limit(1)
              .fetchOne(BandcampPrefixes.BANDCAMP_PREFIXES.BANDCAMP_PREFIX)
      if (fetchOne != null) BandcampPrefix(fetchOne) else null
    }
  }

  private suspend fun <T> runWithConnection(runnable: ((DSLContext) -> T)): T {
    return mutex.withLock { DSL.using(connection.get()).run(runnable) }
  }
}
