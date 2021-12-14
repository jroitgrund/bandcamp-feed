package me.roitgrund.bandcampfeed

import BandcampClient
import com.sun.syndication.feed.synd.*
import com.sun.syndication.io.SyndFeedOutput
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.netty.*
import java.io.OutputStreamWriter
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.html.iframe
import kotlinx.html.stream.appendHTML
import releaseListUrl

fun playerUrl(bandcampRelease: BandcampRelease): URI {
  return URI(
      "https://bandcamp.com/EmbeddedPlayer/v=2/album=${bandcampRelease.id}/size=large/tracklist=true/artwork=small/")
}

fun entry(bandcampRelease: BandcampRelease): SyndEntry {
  val entry = SyndEntryImpl()
  entry.title = "${bandcampRelease.artist} - ${bandcampRelease.title}"
  entry.link = bandcampRelease.url.toString()
  entry.publishedDate = Date.from(bandcampRelease.date.atStartOfDay().toInstant(ZoneOffset.UTC))

  val description = SyndContentImpl()
  description.type = "text/html"
  description.value =
      StringBuilder()
          .appendHTML()
          .iframe {
            src = playerUrl(bandcampRelease).toString()
            height = "400px"
            width = "400px"
          }
          .toString()

  entry.description = description

  return entry
}

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
  val bandcampClient = BandcampClient()
  val storage: Storage = InMemoryStorage()
  routing {
    post("/feeds") {
      call.respondText {
        createFeed(
                storage,
                checkNotNull(call.request.queryParameters.getAll("prefixes"))
                    .map(::BandcampPrefix)
                    .toSet())
            .id
            .toString()
      }
    }

    get("/feeds/{feed-id}") {
      call.respondOutputStream(ContentType.Text.Plain) {
        val feedId = FeedID(UUID.fromString(checkNotNull(call.parameters["feed-id"])))
        val releases = getFeed(feedId, storage, bandcampClient)

        OutputStreamWriter(this).use { it.write(releases.toString()) }
      }
    }

    get("/{bandcamp-prefix}") {
      val bandcampPrefix = BandcampPrefix(checkNotNull(call.parameters["bandcamp-prefix"]))
      val releases: List<BandcampRelease> = listOf()
      call.respondOutputStream(ContentType.Text.Plain) {
        val feed: SyndFeed = SyndFeedImpl()

        feed.title = bandcampPrefix.prefix
        feed.link = releaseListUrl(bandcampPrefix).toString()
        feed.description = bandcampPrefix.prefix
        feed.entries = releases.map(::entry)
        feed.feedType = "rss_2.0"

        val writer = OutputStreamWriter(this)
        val output = SyndFeedOutput()
        output.output(feed, writer)
      }
    }
  }
}

suspend fun getFeed(
    feedId: FeedID,
    storage: Storage,
    bandcampClient: BandcampClient,
): List<BandcampRelease> {
  val prefixes = checkNotNull(storage.getPrefixes(feedId))
  val now = Instant.now()
  val prefixesToUpdate =
      prefixes
          .asSequence()
          .map { (it to storage.getLastUpdated(it)) }
          .filter { (_, date) -> date.isBefore(now.minusMillis(Duration.ofHours(1).toMillis())) }
          .toSet()

  coroutineScope {
    prefixesToUpdate.forEach { (prefix, lastUpdated) ->
      launch {
        bandcampClient
            .getReleases(prefix)
            .map { bandcampClient.getRelease(it) }
            .takeWhile {
              it.date
                  .atStartOfDay(ZoneOffset.UTC)
                  .isAfter(lastUpdated.atZone(ZoneOffset.UTC).minusDays(1))
            }
            .forEach { storage.addRelease(prefix, it) }
      }
    }
  }

  storage.markPrefixesUpdated(prefixesToUpdate.asSequence().map { (prefix, _) -> prefix }.toSet())

  return storage.getReleases(prefixes)
}

fun createFeed(storage: Storage, bandcampPrefixes: Set<BandcampPrefix>) =
    storage.saveFeed(bandcampPrefixes)
