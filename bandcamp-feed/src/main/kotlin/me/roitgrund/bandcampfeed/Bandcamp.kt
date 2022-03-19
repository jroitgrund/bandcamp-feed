package me.roitgrund.bandcampfeed

import io.ktor.http.*
import java.time.LocalDate
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

@Serializable data class BandcampFeed(val name: String, val releases: List<BandcampRelease>)
