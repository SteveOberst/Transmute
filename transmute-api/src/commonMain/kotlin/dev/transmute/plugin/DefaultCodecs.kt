package dev.transmute.plugin

import dev.transmute.audio.*
import dev.transmute.audio.codecs.WavDecoder
import dev.transmute.audio.codecs.WavEncoder
import dev.transmute.image.*
import dev.transmute.image.codecs.bmp.BmpImageDecoder
import dev.transmute.image.codecs.bmp.BmpImageEncoder
import dev.transmute.video.*

/**
 * Populates [decoders] and [encoders] with the platform-native image codecs
 * plus cross-platform fallbacks (BMP).
 */
internal fun installPlatformImageDefaults(
  decoders: MutableImageDecoderRegistry,
  encoders: MutableImageEncoderRegistry,
) {
  installPlatformImageCodecs(decoders, encoders)

  // Cross-platform fallback codecs
  if (decoders.decoderFor(ImageFormat.Bmp) == null) {
    decoders.register(BmpImageDecoder())
  }
  if (encoders.encoderFor(ImageFormat.Bmp) == null) {
    encoders.register(BmpImageEncoder())
  }
}

/**
 * Populates [decoders] and [encoders] with the platform-native audio codecs
 * plus cross-platform fallbacks (WAV).
 */
internal fun installPlatformAudioDefaults(
  decoders: MutableAudioDecoderRegistry,
  encoders: MutableAudioEncoderRegistry,
) {
  // Pure-Kotlin WAV codec works on every target
  decoders.register(WavDecoder())
  encoders.register(WavEncoder())

  installPlatformAudioCodecs(decoders, encoders)
}

/**
 * Populates [decoders] and [encoders] with the platform-native video codecs.
 */
internal fun installPlatformVideoDefaults(
  decoders: MutableVideoDecoderRegistry,
  encoders: MutableVideoEncoderRegistry,
) {
  installPlatformVideoCodecs(decoders, encoders)
}
