package me.roitgrund.bandcampfeed

import io.ktor.http.*
import java.time.LocalDate
import java.util.regex.Pattern
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object UrlSerializer : KSerializer<Url> {
  override fun deserialize(decoder: Decoder): Url {
    return Url(decoder.decodeString())
  }

  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Url", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: Url) {
    encoder.encodeString(value.toString())
  }
}

object LocalDateSerializer : KSerializer<LocalDate> {
  override fun deserialize(decoder: Decoder): LocalDate {
    return LocalDate.parse(decoder.decodeString())
  }

  override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: LocalDate) {
    encoder.encodeString(value.toString())
  }
}

object NextPageKeySerializer : KSerializer<NextPageKey> {
  override fun deserialize(decoder: Decoder): NextPageKey {
    val parts =
        Pattern.compile("[^_]+")
            .matcher(decoder.decodeString())
            .results()
            .map { it.group() }
            .toList()
    return NextPageKey(LocalDate.parse(parts[0]), parts[1])
  }

  override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("NextPageKey", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: NextPageKey) {
    encoder.encodeString("${value.date}_${value.id}")
  }
}

@Serializable(with = NextPageKeySerializer::class)
data class NextPageKey(val date: LocalDate, val id: String)

@Serializable
data class BandcampRelease(
    val id: String,
    @Serializable(with = UrlSerializer::class) val url: Url,
    val title: String,
    val artist: String,
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val prefix: String
)

data class BandcampReleaseIntermediate(
    val id: String,
    val url: Url,
    val title: String,
    val artist: String,
    val prefix: String
)

@Serializable data class BandcampPrefix(val bandcampPrefix: String, val name: String)

@Serializable
data class BandcampFeed(
    val name: String,
    val releases: List<BandcampRelease>,
    val nextPageKey: NextPageKey?
)
