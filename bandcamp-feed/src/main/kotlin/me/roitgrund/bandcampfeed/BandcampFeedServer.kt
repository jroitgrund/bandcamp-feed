package me.roitgrund.bandcampfeed

import BandcampClient
import com.google.common.io.BaseEncoding
import com.sun.syndication.feed.synd.SyndFeed
import com.sun.syndication.feed.synd.SyndFeedImpl
import com.sun.syndication.io.SyndFeedOutput
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.locations.*
import io.ktor.locations.delete as locationsDelete
import io.ktor.locations.post
import io.ktor.locations.put as locationsPut
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.netty.*
import io.ktor.sessions.*
import java.io.OutputStreamWriter
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.flywaydb.core.Flyway
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object BandcampFeedServer {
  val log: Logger = LoggerFactory.getLogger(BandcampFeedServer::class.java)
}

fun main(args: Array<String>): Unit = EngineMain.main(args)

class Locations {

  @Location("/api/new-feed") class NewFeed

  @Location("/api/feed/{feedId}.rss") data class RssFeed(val feedId: String)

  @Location("/api/feed/{feedId}") data class Feed(val feedId: String)

  @Location("/api/feeds") class Feeds

  @Location("/api/user/{user}") data class User(val user: String)

  @Location("/api/oauth-callback") class OAuthCallback

  @Location("/api/login") class Login

  @Location("/") class Home
}

fun ApplicationCall.getUrl(path: String): String {
  val call = this
  return URLBuilder().run {
    this.protocol = URLProtocol.createOrDefault(call.request.origin.scheme)
    this.host =
        call.request.header(HttpHeaders.XForwardedHost)
            ?: checkNotNull(call.request.header(HttpHeaders.Host))
    this.path(path.substring(1))
    this.buildString()
  }
}

@Serializable
data class UserFeed(val id: String, val name: String, val prefixes: Set<BandcampPrefix>)

@Serializable data class UserSession(val email: String)

@Serializable data class FeedRequest(val name: String, val prefixes: Set<String>)

fun maybeHtmlInsideDir(dir: Path): Path? {
  if (dir.isDirectory()) {
    return dir.listDirectoryEntries("*.html").firstOrNull()
  }

  return null
}

fun Application.module() {
  val jsonSerializer = Json { ignoreUnknownKeys = true }
  val httpClient =
      HttpClient(CIO) { install(JsonFeature) { serializer = KotlinxSerializer(jsonSerializer) } }

  install(XForwardedHeaderSupport)
  install(Locations)
  install(Sessions) {
    cookie<UserSession>("user_session") {
      cookie.extensions["SameSite"] = "None"
      cookie.extensions["Secure"] = "true"
      transform(
          SessionTransportTransformerMessageAuthentication(
              BaseEncoding.base64()
                  .decode(environment.config.property("ktor.sessionSalt").getString())))
    }
  }
  install(Authentication) {
    oauth("auth-oauth-google") {
      urlProvider = { this.getUrl(application.locations.href(Locations.OAuthCallback())) }
      providerLookup =
          {
            OAuthServerSettings.OAuth2ServerSettings(
                name = "google",
                authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
                accessTokenUrl = "https://accounts.google.com/o/oauth2/token",
                requestMethod = HttpMethod.Post,
                clientId = environment.config.property("ktor.googleClientId").getString(),
                clientSecret = environment.config.property("ktor.googleClientSecret").getString(),
                defaultScopes = listOf("https://www.googleapis.com/auth/userinfo.email"))
          }
      client = httpClient
    }
    install(ContentNegotiation) { json() }
    install(CachingHeaders) {
      options { outgoingContent ->
        when (outgoingContent.contentType?.withoutParameters()) {
          ContentType.Text.CSS, ContentType.Application.JavaScript ->
              CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 31536000))
          else -> CachingOptions(CacheControl.NoStore(null))
        }
      }
    }
  }

  val bandcampClient = BandcampClient(jsonSerializer, httpClient)
  val dbPath = environment.config.property("ktor.dbPath").getString()
  val dbUrl = "jdbc:sqlite:$dbPath"
  val storage = SqlStorage(dbUrl)
  val development = environment.config.property("ktor.development").getString() == "true"

  Flyway.configure().dataSource(dbUrl, "", "").load().migrate()
  updatePrefixesInBackground(storage, bandcampClient)

  routing {
    authenticate("auth-oauth-google") {
      get<Locations.Login> {}
      get<Locations.OAuthCallback> {
        val token = checkNotNull(call.principal<OAuthAccessTokenResponse.OAuth2>()).accessToken
        val email =
            httpClient
                .get<UserSession>("https://www.googleapis.com/oauth2/v2/userinfo") {
                  headers { append(HttpHeaders.Authorization, "Bearer $token") }
                }
                .email
        call.sessions.set(UserSession(email))
        call.respondRedirect(application.locations.href(Locations.Home()))
      }
    }

    get<Locations.Home> { call.respondFile(Paths.get("/static/index.html").toFile()) }

    static("/static") { files(Paths.get("/static").toFile()) }

    get<Locations.Feeds> {
      val session = call.sessions.get<UserSession>()
      if (session == null) {
        call.response.status(HttpStatusCode.Unauthorized)
      } else {
        call.respond(storage.getUserFeeds(session.email))
      }
    }

    post<Locations.NewFeed> {
      val session = call.sessions.get<UserSession>()
      if (session == null) {
        call.response.status(HttpStatusCode.Unauthorized)
      } else {
        val newFeedRequest = call.receive<FeedRequest>()
        check(newFeedRequest.prefixes.isNotEmpty())
        storage.saveFeed(newFeedRequest.name, session.email, newFeedRequest.prefixes)
        call.response.status(HttpStatusCode.OK)
      }
    }

    locationsPut<Locations.Feed> { feedRequest ->
      val session = call.sessions.get<UserSession>()
      if (session == null) {
        call.response.status(HttpStatusCode.Unauthorized)
      } else {
        val feedRequestBody = call.receive<FeedRequest>()
        if (!storage.editFeed(
            feedRequest.feedId, feedRequestBody.name, session.email, feedRequestBody.prefixes)) {
          call.response.status(HttpStatusCode.NotFound)
        } else {
          call.response.status(HttpStatusCode.OK)
        }
      }
    }

    locationsDelete<Locations.Feed> { feedRequest ->
      val session = call.sessions.get<UserSession>()
      if (session == null) {
        call.response.status(HttpStatusCode.Unauthorized)
      } else {
        if (!storage.deleteFeed(feedRequest.feedId)) {
          call.response.status(HttpStatusCode.NotFound)
        } else {
          call.response.status(HttpStatusCode.OK)
        }
      }
    }

    get<Locations.User> { user ->
      if (call.sessions.get<UserSession>() == null) {
        call.response.status(HttpStatusCode.Unauthorized)
      } else {
        val prefixes = bandcampClient.getArtistsAndLabels(user.user)
        storage.savePrefixes(prefixes)
        call.respond(prefixes)
      }
    }

    get<Locations.Feed> { feedRequest ->
      val pageQuery = call.request.queryParameters["page"]
      val fromPage: NextPageKey? =
          if (pageQuery != null) {
            jsonSerializer.decodeFromString("\"$pageQuery\"")
          } else {
            null
          }
      val fromDateQuery = call.request.queryParameters["fromDate"]
      val fromDate =
          if (fromDateQuery != null) {
            LocalDate.parse(fromDateQuery)
          } else {
            null
          }
      val includePrereleases =
          (call.request.queryParameters["includePrereleases"] ?: "true").toBooleanStrict()
      val feedId = feedRequest.feedId
      val bandcampFeed =
          checkNotNull(storage.getFeedReleases(feedId, fromPage, fromDate, includePrereleases, 10))
      call.respond(bandcampFeed)
    }

    get<Locations.RssFeed> { feedRequest ->
      call.respondOutputStream(ContentType.Application.Rss) {
        val feedId = feedRequest.feedId
        val (name, releases) = checkNotNull(storage.getFeedReleases(feedId, null, null, true, null))

        val feed: SyndFeed = SyndFeedImpl()

        feed.title = name
        feed.link = call.getUrl(application.locations.href(Locations.RssFeed(feedRequest.feedId)))
        feed.description = name
        feed.entries = releases.map(::entry)
        feed.feedType = "rss_2.0"

        val writer = OutputStreamWriter(this)
        val output = SyndFeedOutput()
        output.output(feed, writer)
      }
    }

    get("{...}") {
      val staticPath = Paths.get("/static/", "${call.request.path()}.html")
      val dynamicNoParamPath = maybeHtmlInsideDir(Paths.get("/static/", "${call.request.path()}"))
      val dynamicPath = maybeHtmlInsideDir(Paths.get("/static/", "${call.request.path()}").parent)
      if (staticPath.isRegularFile()) {
        call.respondFile(staticPath.toFile())
      } else if (dynamicNoParamPath?.isRegularFile() == true &&
          Regex("\\[.*\\]").containsMatchIn(dynamicNoParamPath.toString())) {
        call.respondFile(dynamicNoParamPath.toFile())
      } else if (dynamicPath?.isRegularFile() == true &&
          Regex("\\[.*\\]").containsMatchIn(dynamicPath.toString())) {
        call.respondFile(dynamicPath.toFile())
      } else {
        call.response.status(HttpStatusCode.NotFound)
      }
    }
  }
}
