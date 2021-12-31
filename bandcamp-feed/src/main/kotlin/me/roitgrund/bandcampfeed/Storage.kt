package me.roitgrund.bandcampfeed

import io.ktor.http.*
import java.time.LocalDate
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import me.roitgrund.bandcampfeed.sql.tables.*
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.DSL.asterisk
import org.jooq.impl.DSL.inline

data class FeedID(val id: UUID)

data class ReleaseId(val id: String)

interface Storage {
  fun saveFeed(name: String, bandcampPrefixes: Set<BandcampPrefix>): FeedID
  fun addRelease(bandcampPrefix: BandcampPrefix, bandcampRelease: BandcampRelease)

  fun getFeedReleases(feedId: FeedID): Pair<String, List<BandcampRelease>>?
  fun isReleasePresent(releaseId: ReleaseId): Boolean
  fun getNextPrefix(prefix: BandcampPrefix?): BandcampPrefix?
}

class SqlStorage(val url: String) : Storage {
  override fun saveFeed(name: String, bandcampPrefixes: Set<BandcampPrefix>): FeedID {
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

  override fun addRelease(bandcampPrefix: BandcampPrefix, bandcampRelease: BandcampRelease) {
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

  override fun getFeedReleases(feedId: FeedID): Pair<String, List<BandcampRelease>>? {
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
                .sortedByDescending { it.date }
                .toList())
      }
    }
  }

  override fun isReleasePresent(releaseId: ReleaseId): Boolean {
    return runWithConnection { c ->
      c.select(inline("1"))
          .from(Releases.RELEASES)
          .where(Releases.RELEASES.RELEASE_ID.eq(releaseId.id))
          .count() > 0
    }
  }

  override fun getNextPrefix(prefix: BandcampPrefix?): BandcampPrefix? {
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

  private fun <T> runWithConnection(runnable: ((DSLContext) -> T)): T {
    return DSL.using(url).run(runnable)
  }
}

class InMemoryStorage : Storage {

  private val feeds = ConcurrentHashMap<FeedID, Pair<String, Set<BandcampPrefix>>>()
  private val releases = ConcurrentHashMap<ReleaseId, BandcampRelease>()
  private val releasesByPrefix = ConcurrentHashMap<BandcampPrefix, MutableSet<ReleaseId>>()

  override fun getNextPrefix(prefix: BandcampPrefix?): BandcampPrefix? {

    return releasesByPrefix
        .keys()
        .asSequence()
        .sortedBy { it.prefix }
        .dropWhile { prefix != null && it.prefix <= prefix.prefix }
        .firstOrNull()
        ?: releasesByPrefix.keys().asSequence().sortedBy { it.prefix }.firstOrNull()
  }

  override fun saveFeed(name: String, bandcampPrefixes: Set<BandcampPrefix>): FeedID {
    val feedId = FeedID(UUID.randomUUID())
    feeds[feedId] = (name to bandcampPrefixes)
    bandcampPrefixes.forEach { releasesByPrefix.putIfAbsent(it, ConcurrentHashMap.newKeySet()) }
    return feedId
  }

  override fun addRelease(bandcampPrefix: BandcampPrefix, bandcampRelease: BandcampRelease) {
    val releaseId = ReleaseId(bandcampRelease.id)
    releases[releaseId] = bandcampRelease
    releasesByPrefix
        .computeIfAbsent(bandcampPrefix) { ConcurrentHashMap.newKeySet() }
        .add(releaseId)
  }

  override fun getFeedReleases(feedId: FeedID): Pair<String, List<BandcampRelease>>? {
    val feed = feeds[feedId] ?: return null
    val (name, prefixes) = feed
    return (name to
        prefixes
            .asSequence()
            .flatMap { checkNotNull(releasesByPrefix[it]).asSequence() }
            .map { checkNotNull(releases[it]) }
            .groupBy { it.id }
            .mapValues { it.value.first() }
            .values
            .sortedByDescending { it.date }
            .toList())
  }

  override fun isReleasePresent(releaseId: ReleaseId): Boolean {
    return releases.containsKey(releaseId)
  }
}

fun runWithConnectionVoid(runnable: ((DSLContext) -> Unit)): Unit {
  DSL.using("jdbc:sqlite:./bandcamp-feed.sqlite").run(runnable)
}
