package me.roitgrund.bandcampfeed

import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class FeedID(val id: UUID)

interface Storage {
  fun saveFeed(bandcampPrefixes: Set<BandcampPrefix>): FeedID
  fun markPrefixesUpdated(bandcampPrefixes: Set<BandcampPrefix>)
  fun addRelease(bandcampPrefix: BandcampPrefix, bandcampRelease: BandcampRelease)

  fun getPrefixes(feedId: FeedID): Set<BandcampPrefix>?
  fun getLastUpdated(bandcampPrefix: BandcampPrefix): Instant
  fun getReleases(bandcampPrefixes: Set<BandcampPrefix>): List<BandcampRelease>
}

class InMemoryStorage : Storage {

  data class ReleaseId(val id: String)

  private val feeds = ConcurrentHashMap<FeedID, Set<BandcampPrefix>>()
  private val lastUpdated = ConcurrentHashMap<BandcampPrefix, Instant>()
  private val releases = ConcurrentHashMap<ReleaseId, BandcampRelease>()
  private val releasesByPrefix = ConcurrentHashMap<BandcampPrefix, MutableSet<ReleaseId>>()

  override fun saveFeed(bandcampPrefixes: Set<BandcampPrefix>): FeedID {
    val feedId = FeedID(UUID.randomUUID())
    feeds[feedId] = bandcampPrefixes
    return feedId
  }

  override fun markPrefixesUpdated(bandcampPrefixes: Set<BandcampPrefix>) {
    val now = Instant.now()
    bandcampPrefixes.forEach { lastUpdated[it] = now }
  }

  override fun addRelease(bandcampPrefix: BandcampPrefix, bandcampRelease: BandcampRelease) {
    val releaseId = ReleaseId(bandcampRelease.id)
    releases[releaseId] = bandcampRelease
    releasesByPrefix
        .computeIfAbsent(bandcampPrefix) { ConcurrentHashMap.newKeySet() }
        .add(releaseId)
  }

  override fun getPrefixes(feedId: FeedID): Set<BandcampPrefix>? {
    return feeds[feedId]
  }

  override fun getLastUpdated(bandcampPrefix: BandcampPrefix): Instant {
    return lastUpdated[bandcampPrefix] ?: Instant.EPOCH
  }

  override fun getReleases(bandcampPrefixes: Set<BandcampPrefix>): List<BandcampRelease> {
    return bandcampPrefixes
        .asSequence()
        .flatMap { checkNotNull(releasesByPrefix[it]).asSequence() }
        .map { checkNotNull(releases[it]) }
        .sortedByDescending { it.date }
        .toList()
  }
}
