package me.roitgrund.bandcampfeed

import BandcampClient
import com.sun.syndication.feed.synd.*
import com.sun.syndication.io.SyndFeedOutput
import io.ktor.application.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.netty.*
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import java.io.OutputStreamWriter
import java.net.URI
import java.time.ZoneOffset
import java.util.*

object BandcampFeedServer {
  val log = LoggerFactory.getLogger(BandcampFeedServer::class.java)
}

fun playerUrl(bandcampRelease: BandcampRelease): URI {
  return URI(
      "https://bandcamp.com/EmbeddedPlayer/v=2/album=${bandcampRelease.id}/size=large/tracklist=true/artwork=small/")
}

fun entry(bandcampRelease: BandcampRelease): SyndEntry {
  val entry = SyndEntryImpl()
  entry.title =
      "(${bandcampRelease.prefix.prefix}) ${bandcampRelease.artist} - ${bandcampRelease.title}"
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
  val dbPath = environment.config.property("ktor.dbPath").getString()
  val dbUrl = "jdbc:sqlite:$dbPath"
  val storage: Storage = SqlStorage(dbUrl)

  Flyway.configure().dataSource(dbUrl, "", "").load().migrate()
  updatePrefixesInBackground(storage, bandcampClient)

  routing {
    post("/feeds") {
      var parameters = call.receiveParameters()
      call.respondText {
        storage
            .saveFeed(
                checkNotNull(parameters["name"]),
                checkNotNull(parameters.getAll("prefixes")).map(::BandcampPrefix).toSet())
            .id
            .toString()
      }
    }

    get("/feeds/{feed-id}") {
      call.respondOutputStream(ContentType.Application.Rss) {
        val feedId = FeedID(UUID.fromString(checkNotNull(call.parameters["feed-id"])))
        val (name, releases) = checkNotNull(storage.getFeedReleases(feedId))

        val feed: SyndFeed = SyndFeedImpl()

        feed.title = name
        feed.link = "https://bandcamp-feed.roitgrund.me/feeds/${feedId.id}"
        feed.description = name
        feed.entries = releases.map(::entry)
        feed.feedType = "rss_2.0"

        val writer = OutputStreamWriter(this)
        val output = SyndFeedOutput()
        output.output(feed, writer)
      }
    }

    get("/user/{user}") {
      val prefixes = bandcampClient.getArtistsAndLabels(checkNotNull(call.parameters["user"]))
      call.respondHtml {
        head { title { +"Create feed" } }
        body {
          form {
            method = FormMethod.post
            action = "/feeds"
            input {
              name = "name"
              type = InputType.text
              placeholder = "Name..."
            }
            br {}
            br {}
            input {
              type = InputType.submit
              value = "Create feed"
            }
            br {}
            br {}
            prefixes.forEach {
              input {
                type = InputType.checkBox
                name = "prefixes"
                id = it.prefix
                value = it.prefix
              }
              label {
                htmlFor = it.prefix
                +it.prefix
              }
              br {}
            }
          }
        }
      }
    }
  }
}
