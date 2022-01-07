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

interface Storage {
  suspend fun saveFeed(name: String, bandcampPrefixes: Set<BandcampPrefix>): FeedID
  suspend fun addRelease(bandcampPrefix: BandcampPrefix, bandcampRelease: BandcampRelease)

  suspend fun getFeedReleases(feedId: FeedID): Pair<String, List<BandcampRelease>>?
  suspend fun isReleasePresent(releaseId: ReleaseId): Boolean
  suspend fun getNextPrefix(prefix: BandcampPrefix?): BandcampPrefix?
}

class SqlStorage : Storage {
  private val mutex = Mutex()
  private val connection: Supplier<Connection>

  constructor(url: String) {
    connection = Suppliers.memoize { DriverManager.getConnection(url) }
  }
  override suspend fun saveFeed(name: String, bandcampPrefixes: Set<BandcampPrefix>): FeedID {
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

  override suspend fun addRelease(
      bandcampPrefix: BandcampPrefix,
      bandcampRelease: BandcampRelease
  ) {
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

        val joinRecord = dsl.newRecord(ReleasesPrefixesV2.RELEASES_PREFIXES_V2)
        joinRecord.releaseId = bandcampRelease.id
        joinRecord.bandcampPrefix = bandcampPrefix.prefix
        joinRecord.store()
      }
    }
  }

  override suspend fun getFeedReleases(feedId: FeedID): Pair<String, List<BandcampRelease>>? {
    return runWithConnection { c ->
      val releases =
          c.select(asterisk())
              .from(Feeds.FEEDS)
              .join(FeedsPrefixes.FEEDS_PREFIXES)
              .on(Feeds.FEEDS.FEED_ID.eq(FeedsPrefixes.FEEDS_PREFIXES.FEED_ID))
              .join(ReleasesPrefixesV2.RELEASES_PREFIXES_V2)
              .on(
                  FeedsPrefixes.FEEDS_PREFIXES.BANDCAMP_PREFIX.eq(
                      ReleasesPrefixesV2.RELEASES_PREFIXES_V2.BANDCAMP_PREFIX))
              .join(Releases.RELEASES)
              .on(
                  ReleasesPrefixesV2.RELEASES_PREFIXES_V2.RELEASE_ID.eq(
                      Releases.RELEASES.RELEASE_ID))
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
                      BandcampPrefix(
                          it.into(ReleasesPrefixesV2.RELEASES_PREFIXES_V2).bandcampPrefix))
                }
                .sortedByDescending(BandcampRelease::date)
                .toList())
      }
    }
  }

  override suspend fun isReleasePresent(releaseId: ReleaseId): Boolean {
    return runWithConnection { c ->
      c.select(inline("1"))
          .from(Releases.RELEASES)
          .where(Releases.RELEASES.RELEASE_ID.eq(releaseId.id))
          .count() > 0
    }
  }

  override suspend fun getNextPrefix(prefix: BandcampPrefix?): BandcampPrefix? {
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
