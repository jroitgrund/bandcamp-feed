package me.roitgrund.bandcampfeed

import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.sun.syndication.feed.synd.*
import com.sun.syndication.io.SyndFeedOutput
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.netty.*
import kotlinx.html.iframe
import kotlinx.html.stream.appendHTML
import org.apache.commons.text.StringEscapeUtils
import java.io.OutputStreamWriter
import java.net.URI
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.TextStyle
import java.time.temporal.ChronoField
import java.util.*
import java.util.regex.Pattern

data class BandcampPrefix(val prefix: String)

data class BandcampRelease(
    val id: String,
    val uri: URI,
    val imageUri: URI,
    val title: String,
    val artist: String,
    val date: LocalDate
)

fun releaseListUrl(bandcampPrefix: BandcampPrefix): URI {
  return URI("https://${bandcampPrefix.prefix}.bandcamp.com/music")
}

fun playerUrl(bandcampRelease: BandcampRelease): URI {
  return URI(
      "https://bandcamp.com/EmbeddedPlayer/v=2/album=${bandcampRelease.id}/size=large/tracklist=true/artwork=small/")
}

val ITEM_ID_PATTERN: Pattern = Pattern.compile("album-(.*)")
val TITLE_PATTERN: Pattern = Pattern.compile("^\\s*([^\\n]+)\\s*([^\\n]+)")
val DATE_PATTERN: Pattern = Pattern.compile("released (.*)\n")
val DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatterBuilder()
        .appendValue(ChronoField.DAY_OF_MONTH, 2)
        .appendLiteral(' ')
        .appendText(ChronoField.MONTH_OF_YEAR, TextStyle.FULL)
        .appendLiteral(' ')
        .appendValue(ChronoField.YEAR, 4)
        .toFormatter(Locale.ENGLISH)

fun getItemId(attribute: String): String {
  val matcher = ITEM_ID_PATTERN.matcher(attribute)
  check(matcher.matches())
  return checkNotNull(matcher.group(1))
}

fun parseDate(metaDescription: String): LocalDate {
  val matcher = DATE_PATTERN.matcher(metaDescription)
  check(matcher.find())
  val datePart = checkNotNull(matcher.group(1))
  return LocalDate.parse(datePart, DATE_FORMAT)
}

fun cleanUpReleaseUri(releaseUri: String, bandcampPrefix: BandcampPrefix): String {
  val uri =
      URI(
          when {
            releaseUri.startsWith("/") ->
                "https://${bandcampPrefix.prefix}.bandcamp.com${releaseUri}"
            else -> releaseUri
          })
  return URI(uri.scheme, uri.userInfo, uri.host, uri.port, uri.path, null, null).toString()
}

fun parseTitleAndArtist(text: String): Pair<String, String> {
  val matcher = TITLE_PATTERN.matcher(StringEscapeUtils.unescapeHtml4(text))
  check(matcher.find())
  return checkNotNull(matcher.group(1)) to checkNotNull(matcher.group(2))
}

data class BandcampReleaseIntermediate(
    val id: String,
    val uri: URI,
    val imageUri: URI,
    val title: String,
    val artist: String
)

fun getReleases(bandcampPrefix: BandcampPrefix): List<BandcampRelease> {
  return Playwright.create().use { playwright ->
    playwright.firefox().launch().use { browser ->
      val context = browser.newContext()
      val page: Page = context.newPage()
      page.navigate(releaseListUrl(bandcampPrefix).toString())
      val releases =
          page.locator("*[data-item-id]")
              .elementHandles()
              .map {
                val id = getItemId(checkNotNull(it.getAttribute("data-item-id")))
                val releaseUri =
                    cleanUpReleaseUri(
                        checkNotNull(it.querySelector("a")?.getAttribute("href")), bandcampPrefix)
                val imageUri = checkNotNull(it.querySelector("img")?.getAttribute("src"))
                val (title, artist) =
                    parseTitleAndArtist(checkNotNull(it.querySelector("p.title")?.textContent()))
                BandcampReleaseIntermediate(id, URI(releaseUri), URI(imageUri), title, artist)
              }
              .toList()
      releases.map {
        val (id, releaseUri, imageUri, title, artist) = it
        page.navigate(releaseUri.toString())
        val date =
            parseDate(
                checkNotNull(
                    page.locator("meta[name=description]")
                        .elementHandle()
                        ?.getAttribute("content")))

        BandcampRelease(id, releaseUri, imageUri, title, artist, date)
      }
    }
  }
}

fun entry(bandcampRelease: BandcampRelease): SyndEntry {
  val entry = SyndEntryImpl()
  entry.title = "${bandcampRelease.artist} - ${bandcampRelease.title}"
  entry.link = bandcampRelease.uri.toString()
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

fun Application.module(testing: Boolean = false) {
  routing {
    get("/{bandcamp-prefix}") {
      val bandcampPrefix = BandcampPrefix(call.parameters["bandcamp-prefix"]!!)
      val releases = getReleases(bandcampPrefix)
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
