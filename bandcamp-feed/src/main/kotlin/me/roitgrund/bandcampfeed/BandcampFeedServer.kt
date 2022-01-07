package me.roitgrund.bandcampfeed

import BandcampClient
import com.google.common.io.BaseEncoding
import com.sun.syndication.feed.synd.*
import com.sun.syndication.io.SyndFeedOutput
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.features.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.locations.post
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.netty.*
import io.ktor.sessions.*
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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

@Location("/new-feed") class NewFeed

@Location("/feed/{feedId}") data class Feed(val feedId: String)

@Location("/user/{user}") data class User(val user: String)

@Location("/oauth-callback") class OAuthCallback

@Location("/login") class Login

fun ApplicationCall.getUrl(path: String): String {
  val call = this
  return URLBuilder().run {
    this.protocol = URLProtocol.createOrDefault(call.request.origin.scheme)
    this.host = call.request.origin.host
    this.path(path.substring(1))
    this.buildString()
  }
}

@Serializable data class UserSession(val email: String)

fun Application.module() {
  val json = Json { ignoreUnknownKeys = true }
  val httpClient = HttpClient(CIO) { install(JsonFeature) { serializer = KotlinxSerializer(json) } }

  install(Locations)
  install(XForwardedHeaderSupport)
  install(Sessions) {
    cookie<UserSession>("user_session") {
      transform(
          SessionTransportTransformerMessageAuthentication(
              BaseEncoding.base64()
                  .decode(environment.config.property("ktor.sessionSalt").getString())))
    }
  }
  install(Authentication) {
    oauth("auth-oauth-google") {
      urlProvider = { this.getUrl(application.locations.href(OAuthCallback())) }
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
  }

  val bandcampClient = BandcampClient(json, httpClient)
  val dbPath = environment.config.property("ktor.dbPath").getString()
  val dbUrl = "jdbc:sqlite:$dbPath"
  val storage: Storage = SqlStorage(dbUrl)

  Flyway.configure().dataSource(dbUrl, "", "").load().migrate()
  updatePrefixesInBackground(storage, bandcampClient)

  routing {
    authenticate("auth-oauth-google") {
      get<Login> {}
      get<OAuthCallback> {
        val token = checkNotNull(call.principal<OAuthAccessTokenResponse.OAuth2>()).accessToken
        val email =
            httpClient
                .get<UserSession>("https://www.googleapis.com/oauth2/v2/userinfo") {
                  headers { append(HttpHeaders.Authorization, "Bearer $token") }
                }
                .email
        call.sessions.set(UserSession(email))
        call.respondRedirect(application.locations.href(User("jonathan")))
      }
    }

    post<NewFeed> {
      var parameters = call.receiveParameters()
      call.respondText {
        call.getUrl(
            application.locations.href(
                Feed(
                    storage
                        .saveFeed(
                            checkNotNull(parameters["name"]),
                            checkNotNull(parameters.getAll("prefixes"))
                                .map(::BandcampPrefix)
                                .toSet())
                        .id
                        .toString())))
      }
    }

    get<Feed> { feedRequest ->
      call.respondOutputStream(ContentType.Application.Rss) {
        val feedId = FeedID(UUID.fromString(feedRequest.feedId))
        val (name, releases) = checkNotNull(storage.getFeedReleases(feedId))

        val feed: SyndFeed = SyndFeedImpl()

        feed.title = name
        feed.link = call.getUrl(application.locations.href(Feed(feedRequest.feedId)))
        feed.description = name
        feed.entries = releases.map(::entry)
        feed.feedType = "rss_2.0"

        val writer = OutputStreamWriter(this)
        val output = SyndFeedOutput()
        output.output(feed, writer)
      }
    }

    get<User> { user ->
      if (call.sessions.get<UserSession>() == null) {
        call.respondRedirect(application.locations.href(Login()))
      } else {

        val prefixes = bandcampClient.getArtistsAndLabels(user.user)
        call.respondHtml {
          head { title { +"Create feed" } }
          body {
            form {
              method = FormMethod.post
              action = application.locations.href(NewFeed())
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
}
