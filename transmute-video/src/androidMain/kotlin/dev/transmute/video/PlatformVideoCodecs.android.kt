package dev.transmute.video

import dev.transmute.video.codecs.android.AndroidMovCodec
import dev.transmute.video.codecs.android.AndroidMp4Codec
import dev.transmute.video.codecs.android.AndroidWebmDecoder

actual fun installPlatformVideoCodecs(
  decoders: MutableVideoDecoderRegistry,
  encoders: MutableVideoEncoderRegistry,
) {
  // MP4 - full codec (H.264/AAC via MediaCodec + MediaMuxer)
  val mp4 = AndroidMp4Codec()
  decoders.register(mp4)
  encoders.register(mp4)

  // MOV - full codec (same H.264/AAC pipeline)
  val mov = AndroidMovCodec()
  decoders.register(mov)
  encoders.register(mov)

  // WebM - decode-only (VP8/VP9 supported by MediaCodec on most devices)
  decoders.register(AndroidWebmDecoder())
}
