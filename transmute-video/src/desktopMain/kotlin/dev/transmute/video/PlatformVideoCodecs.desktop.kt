package dev.transmute.video

import dev.transmute.video.codecs.jvm.FfmpegVideoEngine
import dev.transmute.video.codecs.jvm.JvmAviCodec
import dev.transmute.video.codecs.jvm.JvmMkvCodec
import dev.transmute.video.codecs.jvm.JvmMovCodec
import dev.transmute.video.codecs.jvm.JvmMp4Codec
import dev.transmute.video.codecs.jvm.JvmWebmCodec

actual fun installPlatformVideoCodecs(
  decoders: MutableVideoDecoderRegistry,
  encoders: MutableVideoEncoderRegistry,
) {
  // Always register decoders for format detection (sniffing doesn't need FFmpeg).
  // Decode/encode will throw at runtime if FFmpeg is absent.
  val mp4 = JvmMp4Codec()
  decoders.register(mp4)

  val mov = JvmMovCodec()
  decoders.register(mov)

  val webm = JvmWebmCodec()
  decoders.register(webm)

  val avi = JvmAviCodec()
  decoders.register(avi)

  val mkv = JvmMkvCodec()
  decoders.register(mkv)

  // Only register encoders when FFmpeg is available.
  if (FfmpegVideoEngine.available) {
    encoders.register(mp4)
    encoders.register(mov)
    encoders.register(webm)
    encoders.register(avi)
    encoders.register(mkv)
  }
}
