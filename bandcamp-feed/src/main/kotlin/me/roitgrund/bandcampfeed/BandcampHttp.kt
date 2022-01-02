import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.common.base.Suppliers
import com.google.common.util.concurrent.RateLimiter
import io.ktor.client.*
import io.ktor.client.call.*
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

private val OBJECT_MAPPER = Suppliers.memoize(::jacksonObjectMapper)
private val URL_PREFIX_PATTERN: Pattern = Pattern.compile("://(.*).bandcamp.com")
private val ITEM_ID_PATTERN: Pattern = Pattern.compile("(album|track)-(.*)")
private val TITLE_PATTERN: Pattern = Pattern.compile("^(.*) <br>.*> (.*) </span")
private val DATE_PATTERN: Pattern = Pattern.compile("(released|releases) (\\d.*)\n")
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

@JsonIgnoreProperties(ignoreUnknown = true)
private data class PageData(
    @JsonProperty("following_bands_data") val followingBandsData: FollowingBandsData,
    @JsonProperty("fan_data") val fanData: FanData
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class FanData(@JsonProperty("fan_id") val fanId: Int)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class FollowingBandsData(@JsonProperty("last_token") val lastToken: String)

private data class FollowingBandsRequest(
    @JsonProperty("fan_id") val fanId: Int,
    @JsonProperty("older_than_token") val olderThanToken: String,
    val count: Int
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class FollowingBandsResponse(
    @JsonProperty("followeers") val followers: List<Follower>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class Follower(
    @JsonProperty("url_hints") val urlHints: UrlHints,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class UrlHints(
    @JsonProperty("subdomain") val bandcampPrefix: String,
)

class BandcampClient {
  private val rateLimiter = RateLimiter.create(2.0)
  @Volatile private var highPriActionsOngoing: Int = 0

  private suspend fun <T> hiPri(action: (suspend () -> T)): T {
    highPriActionsOngoing++
    try {
      return action()
    } finally {
      highPriActionsOngoing--
    }
  }

  private suspend fun <T> loPri(action: (suspend () -> T)): T {
    while (highPriActionsOngoing > 0) {
      delay(100L)
    }

    return action()
  }

  private suspend fun getHtml(url: Url): String {
    while (!rateLimiter.tryAcquire()) {
      delay(100L)
    }

    return HttpClient(CIO).use { it.get<HttpStatement>(url).receive() }
  }

  suspend fun getArtistsAndLabels(username: String): List<BandcampPrefix> {
    return hiPri {
      val parsedPage =
          Jsoup.parse(getHtml(Url("https://bandcamp.com/${username}/following/artists_and_labels")))
      val pageData =
          OBJECT_MAPPER
              .get()
              .readValue(
                  StringEscapeUtils.unescapeHtml4(parsedPage.select("#pagedata").attr("data-blob")),
                  PageData::class.java)
      val fanId = pageData.fanData.fanId

      OBJECT_MAPPER
          .get()
          .readValue(
              HttpClient(CIO)
                  .use<HttpClient, HttpResponse> {
                    it.post("https://bandcamp.com/api/fancollection/1/following_bands") {
                      body =
                          OBJECT_MAPPER
                              .get()
                              .writeValueAsString(
                                  FollowingBandsRequest(
                                      fanId, "9999999999:9999999999", Int.MAX_VALUE))
                    }
                  }
                  .receive<String>(),
              FollowingBandsResponse::class.java)
          .followers
          .asSequence()
          .map { BandcampPrefix(it.urlHints.bandcampPrefix) }
          .sortedBy { it.prefix }
          .toList()
    }
  }

  suspend fun getReleases(bandcampPrefix: BandcampPrefix): List<BandcampReleaseIntermediate> {
    return loPri {
      Jsoup.parse(getHtml(releaseListUrl(bandcampPrefix)))
          .body()
          .select("*[data-item-id]")
          .map {
            val id = getItemId(checkNotNull(it.attr("data-item-id")))
            val releaseUri =
                cleanUpReleaseUri(checkNotNull(it.selectFirst("a")?.attr("href")), bandcampPrefix)
            val (title, artist) =
                parseTitleAndArtist(checkNotNull(it.selectFirst("p.title")?.html()))
            BandcampReleaseIntermediate(id, Url(releaseUri), title, artist, bandcampPrefix)
          }
          .toList()
    }
  }

  suspend fun getRelease(bandcampRelease: BandcampReleaseIntermediate): BandcampRelease {
    val (id, url, title, artist, prefix) = bandcampRelease
    return loPri {
      BandcampRelease(
          id,
          url,
          title,
          artist,
          parseDate(
              checkNotNull(
                  Jsoup.parse(getHtml(bandcampRelease.url))
                      .head()
                      .selectFirst("meta[name=description]")
                      ?.attr("content"))),
          prefix)
    }
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
