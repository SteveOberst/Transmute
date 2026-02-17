package dev.transmute.audio

import dev.transmute.audio.codecs.android.AndroidAacCodec
import dev.transmute.audio.codecs.android.AndroidFlacCodec
import dev.transmute.audio.codecs.android.AndroidM4aCodec
import dev.transmute.audio.codecs.android.AndroidMp3Codec
import dev.transmute.audio.codecs.android.AndroidOggDecoder
import dev.transmute.audio.codecs.android.AndroidOpusCodec

actual fun installPlatformAudioCodecs(
  decoders: MutableAudioDecoderRegistry,
  encoders: MutableAudioEncoderRegistry,
) {
  // Decode-only formats (no Android encoder available).
  decoders.register(AndroidOggDecoder())

  // Full codecs - hardware-accelerated decode + encode via MediaCodec.
  val mp3Codec = AndroidMp3Codec()
  decoders.register(mp3Codec)
  encoders.register(mp3Codec)

  // Full codecs - hardware-accelerated decode + encode via MediaCodec.
  val flacCodec = AndroidFlacCodec()
  decoders.register(flacCodec)
  encoders.register(flacCodec)

  val opusCodec = AndroidOpusCodec()
  decoders.register(opusCodec)
  if (AndroidOpusCodec.canEncode) {
    encoders.register(opusCodec)
  }

  val aacCodec = AndroidAacCodec()
  decoders.register(aacCodec)
  encoders.register(aacCodec)

  val m4aCodec = AndroidM4aCodec()
  decoders.register(m4aCodec)
  encoders.register(m4aCodec)
}
