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

class SqlStorage(url: String) {
  private val mutex = Mutex()
  private val connection: Supplier<Connection>

  init {
    connection =
        Suppliers.memoize {
          val connection = DriverManager.getConnection(url)
          DSL.using(DriverManager.getConnection(url)).query("PRAGMA foreign_keys = ON;").execute()
          connection
        }
  }

  suspend fun getUserFeeds(email: String): List<UserFeed> {
    return runWithConnection { c ->
      c
          .select(
              Feeds.FEEDS.FEED_ID,
              Feeds.FEEDS.FEED_NAME,
              FeedsPrefixes.FEEDS_PREFIXES.BANDCAMP_PREFIX,
              BandcampPrefixes.BANDCAMP_PREFIXES.NAME)
          .from(Feeds.FEEDS)
          .join(FeedsPrefixes.FEEDS_PREFIXES)
          .on(Feeds.FEEDS.FEED_ID.eq(FeedsPrefixes.FEEDS_PREFIXES.FEED_ID))
          .join(BandcampPrefixes.BANDCAMP_PREFIXES)
          .on(
              FeedsPrefixes.FEEDS_PREFIXES.BANDCAMP_PREFIX.eq(
                  BandcampPrefixes.BANDCAMP_PREFIXES.BANDCAMP_PREFIX))
          .where(Feeds.FEEDS.USER_EMAIL.eq(email))
          .fetchGroups(Feeds.FEEDS.FEED_ID)
          .values
          .map {
            UserFeed(
                it.first().into(Feeds.FEEDS).feedId,
                it.first().into(Feeds.FEEDS).feedName,
                it.asSequence()
                    .map { row ->
                      BandcampPrefix(
                          row.into(FeedsPrefixes.FEEDS_PREFIXES).bandcampPrefix,
                          row.into(BandcampPrefixes.BANDCAMP_PREFIXES).name)
                    }
                    .toSet())
          }
          .sortedBy { it.name }
    }
  }

  suspend fun savePrefixes(bandcampPrefixes: Set<BandcampPrefix>) {
    runWithConnection { c ->
      c.loadInto(BandcampPrefixes.BANDCAMP_PREFIXES)
          .batchAll()
          .onDuplicateKeyIgnore()
          .loadRecords(
              bandcampPrefixes.map {
                val newRecord = c.newRecord(BandcampPrefixes.BANDCAMP_PREFIXES)
                newRecord.bandcampPrefix = it.bandcampPrefix
                newRecord.name = it.name
                newRecord
              })
          .fields(BandcampPrefixes.BANDCAMP_PREFIXES.fields().toList())
          .execute()
    }
  }

  suspend fun saveFeed(name: String, email: String, bandcampPrefixes: Set<String>): String {
    return runWithConnection { c ->
      c.transactionResult { tx ->
        val dsl = DSL.using(tx)

        val feedId = UUID.randomUUID().toString()
        val feed = dsl.newRecord(Feeds.FEEDS)
        feed.feedName = name
        feed.userEmail = email
        feed.feedId = feedId
        feed.store()

        dsl.batchStore(
                bandcampPrefixes.map {
                  val feedPrefix = dsl.newRecord(FeedsPrefixes.FEEDS_PREFIXES)
                  feedPrefix.bandcampPrefix = it
                  feedPrefix.feedId = feedId
                  feedPrefix
                })
            .execute()

        feedId
      }
    }
  }

  suspend fun editFeed(
      feedId: String,
      name: String,
      email: String,
      bandcampPrefixes: Set<String>
  ): Boolean {
    return runWithConnection { c ->
      c.transactionResult { tx ->
        val dsl = DSL.using(tx)
        val existingEmail =
            dsl.select(Feeds.FEEDS.USER_EMAIL)
                .from(Feeds.FEEDS)
                .where(Feeds.FEEDS.FEED_ID.eq(feedId))
                .fetchOne()
                ?.component1()
        if (existingEmail == null || email != existingEmail) {
          false
        } else {
          dsl.deleteFrom(FeedsPrefixes.FEEDS_PREFIXES)
              .where(FeedsPrefixes.FEEDS_PREFIXES.FEED_ID.eq(feedId))
              .execute()
          dsl.batchStore(
                  bandcampPrefixes.map {
                    val feedPrefix = dsl.newRecord(FeedsPrefixes.FEEDS_PREFIXES)
                    feedPrefix.bandcampPrefix = it
                    feedPrefix.feedId = feedId
                    feedPrefix
                  })
              .execute()
          dsl.update(Feeds.FEEDS)
              .set(Feeds.FEEDS.FEED_NAME, name)
              .where(Feeds.FEEDS.FEED_ID.eq(feedId))
              .execute()
          true
        }
      }
    }
  }

  suspend fun addRelease(bandcampPrefix: String, bandcampRelease: BandcampRelease) {
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
        joinRecord.bandcampPrefix = bandcampPrefix
        joinRecord.store()
      }
    }
  }

  suspend fun getFeedReleases(
      feedId: String,
      fromId: String?,
      fromDate: String?,
      pageSize: Int?
  ): Pair<String, List<BandcampRelease>>? {
    return runWithConnection { c ->
      val select =
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
              .where(Feeds.FEEDS.FEED_ID.eq(feedId))
      val paginatedSelect =
          if (fromDate != null && fromId != null) {
            select
                .and(Releases.RELEASES.RELEASE_DATE.lt(fromDate))
                .or(
                    Releases.RELEASES
                        .RELEASE_DATE
                        .eq(fromDate)
                        .and(Releases.RELEASES.RELEASE_ID.lt(fromId)))
          } else {
            select
          }
      val orderedSelect =
          paginatedSelect.orderBy(
              Releases.RELEASES.RELEASE_DATE.desc(), Releases.RELEASES.RELEASE_ID.desc())
      val limitedSelect =
          if (pageSize != null) {
            orderedSelect.limit(pageSize)
          } else {
            orderedSelect
          }
      val releases = limitedSelect.fetch().toList()
      if (releases.isEmpty()) {
        val name =
            c.select(Feeds.FEEDS.FEED_NAME)
                .from(Feeds.FEEDS)
                .where(Feeds.FEEDS.FEED_ID.eq(feedId))
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
                      it.into(ReleasesPrefixes.RELEASES_PREFIXES).bandcampPrefix)
                }
                .toList())
      }
    }
  }

  suspend fun isReleasePresent(releaseId: String): Boolean {
    return runWithConnection { c ->
      c.select(inline("1"))
          .from(Releases.RELEASES)
          .where(Releases.RELEASES.RELEASE_ID.eq(releaseId))
          .fetchOne() != null
    }
  }

  suspend fun getNextPrefix(prefix: String?): String? {
    return runWithConnection { c ->
      c.select(BandcampPrefixes.BANDCAMP_PREFIXES.BANDCAMP_PREFIX)
          .from(BandcampPrefixes.BANDCAMP_PREFIXES)
          .join(FeedsPrefixes.FEEDS_PREFIXES)
          .on(
              BandcampPrefixes.BANDCAMP_PREFIXES.BANDCAMP_PREFIX.eq(
                  FeedsPrefixes.FEEDS_PREFIXES.BANDCAMP_PREFIX))
          .where(BandcampPrefixes.BANDCAMP_PREFIXES.BANDCAMP_PREFIX.greaterThan(prefix ?: ""))
          .groupBy(BandcampPrefixes.BANDCAMP_PREFIXES.BANDCAMP_PREFIX)
          .unionAll(
              c.select(BandcampPrefixes.BANDCAMP_PREFIXES.BANDCAMP_PREFIX)
                  .from(BandcampPrefixes.BANDCAMP_PREFIXES)
                  .join(FeedsPrefixes.FEEDS_PREFIXES)
                  .on(
                      BandcampPrefixes.BANDCAMP_PREFIXES.BANDCAMP_PREFIX.eq(
                          FeedsPrefixes.FEEDS_PREFIXES.BANDCAMP_PREFIX))
                  .groupBy(BandcampPrefixes.BANDCAMP_PREFIXES.BANDCAMP_PREFIX))
          .limit(2)
          .fetch(BandcampPrefixes.BANDCAMP_PREFIXES.BANDCAMP_PREFIX)
          .firstOrNull()
    }
  }

  suspend fun deleteFeed(feedId: String): Boolean {
    return runWithConnection { c ->
      c.deleteFrom(Feeds.FEEDS).where(Feeds.FEEDS.FEED_ID.eq(feedId)).execute() == 1
    }
  }

  private suspend fun <T> runWithConnection(runnable: ((DSLContext) -> T)): T {
    return mutex.withLock { DSL.using(connection.get()).run(runnable) }
  }
}
