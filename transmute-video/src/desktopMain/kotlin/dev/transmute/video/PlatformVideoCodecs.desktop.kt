package dev.transmute.video

actual fun installPlatformVideoCodecs(
  decoders: MutableVideoDecoderRegistry,
  encoders: MutableVideoEncoderRegistry,
) {
  // Desktop has no native video codecs.
  // All video decode/encode is provided by the optional transmute-gstreamer
  // module. Add it as a dependency and configure via
  // TransmuteContext { gstreamer() } to enable MP4, MOV, WebM, AVI, MKV.
}
