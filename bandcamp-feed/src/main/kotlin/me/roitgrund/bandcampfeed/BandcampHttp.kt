import com.google.common.util.concurrent.RateLimiter
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.TextStyle
import java.time.temporal.ChronoField
import java.util.*
import java.util.regex.Pattern
import kotlinx.coroutines.delay
import me.roitgrund.bandcampfeed.BandcampPrefix
import me.roitgrund.bandcampfeed.BandcampRelease
import me.roitgrund.bandcampfeed.BandcampReleaseIntermediate
import org.apache.commons.text.StringEscapeUtils
import org.jsoup.Jsoup

private val ITEM_ID_PATTERN: Pattern = Pattern.compile("(album|track)-(.*)")
private val TITLE_PATTERN: Pattern = Pattern.compile("^(.*) <br>.*> (.*) </span")
private val DATE_PATTERN: Pattern = Pattern.compile("(released|releases) (.*)\n")
private val DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatterBuilder()
        .appendValue(ChronoField.DAY_OF_MONTH, 2)
        .appendLiteral(' ')
        .appendText(ChronoField.MONTH_OF_YEAR, TextStyle.FULL)
        .appendLiteral(' ')
        .appendValue(ChronoField.YEAR, 4)
        .toFormatter(Locale.ENGLISH)

fun releaseListUrl(bandcampPrefix: BandcampPrefix): Url {
  return Url("https://${bandcampPrefix.prefix}.bandcamp.com/music")
}

class BandcampClient {
  private val rateLimiter = RateLimiter.create(2.0)

  private suspend fun getHtml(url: Url): String {
    while (!rateLimiter.tryAcquire()) {
      delay(100L)
    }

    return HttpClient(CIO).use { it.get<HttpStatement>(url).receive() }
  }

  suspend fun getReleases(bandcampPrefix: BandcampPrefix): List<BandcampReleaseIntermediate> {
    return Jsoup.parse(getHtml(releaseListUrl(bandcampPrefix)))
        .body()
        .select("*[data-item-id]")
        .map {
          val id = getItemId(checkNotNull(it.attr("data-item-id")))
          val releaseUri =
              cleanUpReleaseUri(checkNotNull(it.selectFirst("a")?.attr("href")), bandcampPrefix)
          val (title, artist) = parseTitleAndArtist(checkNotNull(it.selectFirst("p.title")?.html()))
          BandcampReleaseIntermediate(id, Url(releaseUri), title, artist)
        }
        .toList()
  }

  suspend fun getRelease(bandcampRelease: BandcampReleaseIntermediate): BandcampRelease {
    val (id, url, title, artist) = bandcampRelease
    return BandcampRelease(
        id,
        url,
        title,
        artist,
        parseDate(
            checkNotNull(
                Jsoup.parse(getHtml(bandcampRelease.url))
                    .head()
                    .selectFirst("meta[name=description]")
                    ?.attr("content"))))
  }
}

private fun getItemId(attribute: String): String {
  val matcher = ITEM_ID_PATTERN.matcher(attribute)
  try {
    check(matcher.matches())
  } catch (e: IllegalStateException) {
    throw IllegalStateException(
        String.format("Couldn't parse item id from attribute '%s'", attribute), e)
  }
  return checkNotNull(matcher.group(2))
}

private fun parseDate(metaDescription: String): LocalDate {
  val matcher = DATE_PATTERN.matcher(metaDescription)
  try {
    check(matcher.find())
  } catch (e: IllegalStateException) {
    throw IllegalStateException(
        String.format("Couldn't parse date from description '%s'", metaDescription), e)
  }
  val datePart = checkNotNull(matcher.group(2))
  return LocalDate.parse(datePart, DATE_FORMAT)
}

private fun cleanUpReleaseUri(releaseUri: String, bandcampPrefix: BandcampPrefix): String {
  val uri =
      URI(
          when {
            releaseUri.startsWith("/") ->
                "https://${bandcampPrefix.prefix}.bandcamp.com${releaseUri}"
            else -> releaseUri
          })
  return URI(uri.scheme, uri.userInfo, uri.host, uri.port, uri.path, null, null).toString()
}

private fun parseTitleAndArtist(text: String): Pair<String, String> {
  val unescapedText = StringEscapeUtils.unescapeHtml4(text)
  val matcher = TITLE_PATTERN.matcher(unescapedText)
  val matches = matcher.find()
  return if (matches) {
    checkNotNull(matcher.group(1)) to checkNotNull(matcher.group(2))
  } else {
    unescapedText to "Various"
  }
}
