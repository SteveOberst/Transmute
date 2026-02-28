package dev.transmute.video

actual fun installPlatformVideoCodecs(
  decoders: MutableVideoDecoderRegistry,
  encoders: MutableVideoEncoderRegistry,
) {
  // Desktop/JVM has no built-in video codecs.
  // Use the GStreamer plugin for video support:
  //   transmute { plugins { install(GStreamer) } }
}
