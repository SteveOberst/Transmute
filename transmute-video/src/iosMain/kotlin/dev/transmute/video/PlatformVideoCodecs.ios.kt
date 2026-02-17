package dev.transmute.video

import dev.transmute.video.codecs.ios.IosMovCodec
import dev.transmute.video.codecs.ios.IosMp4Codec

actual fun installPlatformVideoCodecs(
  decoders: MutableVideoDecoderRegistry,
  encoders: MutableVideoEncoderRegistry,
) {
  // MP4 - full codec (H.264/AAC via AVFoundation)
  val mp4 = IosMp4Codec()
  decoders.register(mp4)
  encoders.register(mp4)

  // MOV - full codec (H.264/AAC via AVFoundation)
  val mov = IosMovCodec()
  decoders.register(mov)
  encoders.register(mov)
}
