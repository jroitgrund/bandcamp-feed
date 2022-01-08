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
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.locations.post
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.netty.*
import io.ktor.sessions.*
import java.io.OutputStreamWriter
import java.util.*
import kotlinx.html.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory

object BandcampFeedServer {
  val log = LoggerFactory.getLogger(BandcampFeedServer::class.java)
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

@Serializable data class FeedRequest(val name: String, val prefixes: Set<String>)

fun Application.module() {
  val jsonSerializer = Json { ignoreUnknownKeys = true }
  val httpClient =
      HttpClient(CIO) { install(JsonFeature) { serializer = KotlinxSerializer(jsonSerializer) } }

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
    install(ContentNegotiation) { json() }
  }

  val bandcampClient = BandcampClient(jsonSerializer, httpClient)
  val dbPath = environment.config.property("ktor.dbPath").getString()
  val dbUrl = "jdbc:sqlite:$dbPath"
  val storage = SqlStorage(dbUrl)

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
      val session = call.sessions.get<UserSession>()
      if (session == null) {
        call.respondRedirect(application.locations.href(Login()))
      } else {
        var newFeedRequest = call.receive<FeedRequest>()
        call.respond(
            storage
                .saveFeed(
                    newFeedRequest.name,
                    session.email,
                    newFeedRequest.prefixes.map(::BandcampPrefix).toSet())
                .id
                .toString())
      }
    }

    post<Feed> { feedRequest ->
      val session = call.sessions.get<UserSession>()
      if (session == null) {
        call.respondRedirect(application.locations.href(Login()))
      } else {
        var feedRequestBody = call.receive<FeedRequest>()
        if (!storage.editFeed(
            FeedID(UUID.fromString(feedRequest.feedId)),
            feedRequestBody.name,
            session.email,
            feedRequestBody.prefixes.map(::BandcampPrefix).toSet())) {
          call.response.status(HttpStatusCode.NotFound)
        } else {
          call.response.status(HttpStatusCode.OK)
        }
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
