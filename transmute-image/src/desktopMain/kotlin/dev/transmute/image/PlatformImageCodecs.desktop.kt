package dev.transmute.image

import dev.transmute.image.codecs.jvm.JvmImageIoDecoder
import dev.transmute.image.codecs.jvm.JvmImageIoEncoder

actual fun installPlatformImageCodecs(decoders: MutableImageDecoderRegistry, encoders: MutableImageEncoderRegistry) {
  decoders.register(JvmImageIoDecoder())
  encoders.register(JvmImageIoEncoder())
}
