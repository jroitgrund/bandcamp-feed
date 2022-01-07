import com.google.common.util.concurrent.RateLimiter
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import me.roitgrund.bandcampfeed.BandcampPrefix
import me.roitgrund.bandcampfeed.BandcampRelease
import me.roitgrund.bandcampfeed.BandcampReleaseIntermediate
import org.apache.commons.text.StringEscapeUtils
import org.jsoup.Jsoup
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.TextStyle
import java.time.temporal.ChronoField
import java.util.*
import java.util.regex.Pattern

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

@Serializable
private data class PageData(
    @SerialName("following_bands_data") val followingBandsData: FollowingBandsData,
    @SerialName("fan_data") val fanData: FanData
)

@Serializable private data class FanData(@SerialName("fan_id") val fanId: Int)

@Serializable
private data class FollowingBandsData(@SerialName("last_token") val lastToken: String)

@Serializable
private data class FollowingBandsRequest(
    @SerialName("fan_id") val fanId: Int,
    @SerialName("older_than_token") val olderThanToken: String,
    val count: Int
)

@Serializable
private data class FollowingBandsResponse(
    @SerialName("followeers") val followers: List<Follower>,
)

@Serializable
private data class Follower(
    @SerialName("url_hints") val urlHints: UrlHints,
)

@Serializable
private data class UrlHints(
    @SerialName("subdomain") val bandcampPrefix: String,
)

class BandcampClient(private val json: Json, private val client: HttpClient) {
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

  private suspend fun <T> useClient(withClient: (suspend (client: HttpClient) -> T)): T {
    while (!rateLimiter.tryAcquire()) {
      delay(100L)
    }

    return withClient(client)
  }

  private suspend fun getHtml(url: Url): String {
    while (!rateLimiter.tryAcquire()) {
      delay(100L)
    }

    return useClient { it.get<HttpStatement>(url).receive() }
  }

  suspend fun getArtistsAndLabels(username: String): List<BandcampPrefix> {
    return hiPri {
      val parsedPage =
          Jsoup.parse(getHtml(Url("https://bandcamp.com/${username}/following/artists_and_labels")))
      val pageData: PageData =
          json.decodeFromString(
              StringEscapeUtils.unescapeHtml4(parsedPage.select("#pagedata").attr("data-blob")))
      val fanId = pageData.fanData.fanId

      useClient {
            it.post<FollowingBandsResponse>(
                "https://bandcamp.com/api/fancollection/1/following_bands") {
              contentType(ContentType.Application.Json)
              body = FollowingBandsRequest(fanId, "9999999999:9999999999", Int.MAX_VALUE)
            }
          }
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
