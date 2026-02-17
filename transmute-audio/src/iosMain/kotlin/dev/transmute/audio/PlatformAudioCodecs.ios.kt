package dev.transmute.audio

import dev.transmute.audio.codecs.ios.IosAacCodec
import dev.transmute.audio.codecs.ios.IosFlacCodec
import dev.transmute.audio.codecs.ios.IosM4aCodec
import dev.transmute.audio.codecs.ios.IosMp3Decoder

actual fun installPlatformAudioCodecs(
  decoders: MutableAudioDecoderRegistry,
  encoders: MutableAudioEncoderRegistry,
) {
  // Decode-only formats (no iOS encoder available).
  decoders.register(IosMp3Decoder())

  // Full codecs - hardware-accelerated decode + encode via AVAssetReader/Writer.
  val flacCodec = IosFlacCodec()
  decoders.register(flacCodec)
  encoders.register(flacCodec)

  val aacCodec = IosAacCodec()
  decoders.register(aacCodec)
  encoders.register(aacCodec)

  val m4aCodec = IosM4aCodec()
  decoders.register(m4aCodec)
  encoders.register(m4aCodec)
}
