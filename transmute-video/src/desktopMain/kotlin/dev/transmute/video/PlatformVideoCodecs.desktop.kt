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
  // All video codecs require FFmpeg on PATH.
  if (!FfmpegVideoEngine.available) return

  val mp4 = JvmMp4Codec()
  decoders.register(mp4)
  encoders.register(mp4)

  val mov = JvmMovCodec()
  decoders.register(mov)
  encoders.register(mov)

  val webm = JvmWebmCodec()
  decoders.register(webm)
  encoders.register(webm)

  val avi = JvmAviCodec()
  decoders.register(avi)
  encoders.register(avi)

  val mkv = JvmMkvCodec()
  decoders.register(mkv)
  encoders.register(mkv)
}
