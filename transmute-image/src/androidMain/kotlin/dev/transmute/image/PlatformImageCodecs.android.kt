package dev.transmute.image

import dev.transmute.image.codecs.android.AndroidBitmapImageDecoder
import dev.transmute.image.codecs.android.AndroidBitmapImageEncoder

actual fun installPlatformImageCodecs(decoders: MutableImageDecoderRegistry, encoders: MutableImageEncoderRegistry) {
  // Decoding: Android BitmapFactory supports many formats; we register the common ones.
  decoders.register(AndroidBitmapImageDecoder())
  encoders.register(AndroidBitmapImageEncoder())
}
