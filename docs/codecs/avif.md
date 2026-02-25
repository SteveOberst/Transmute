# AVIF

AVIF is a next-generation image format based on AV1. It provides excellent compression efficiency and high quality, especially for photographic content.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ❌     | ImageDecoder (decode only) |
| Desktop  | ✅     | ✅     | GStreamer (requires `transmute-gstreamer`) |
| iOS      | ✅     | ✅     | CoreGraphics (CGImage) |

## Usage

```kotlin
// Convert any image to AVIF (Desktop/iOS)
suspend fun convertToAvif(inputBytes: ByteArray): ByteArray =
  Transmute.image {
    encode { options(HeifEncodeOptions(format = ImageFormat.Avif, quality = 0.8f)) }
  }.transmute(inputBytes.asBytes()).bytes.data

// Decode AVIF (re-encode to PNG)
suspend fun decodeToPng(avifBytes: ByteArray): ByteArray =
  Transmute.image {
    encode { options(PngEncodeOptions()) }
  }.transmute(avifBytes.asBytes()).bytes.data
```

## Notes

- Android decode support depends on OS version; encoding support is limited.
- Desktop requires the optional `transmute-gstreamer` module with GStreamer installed.
- iOS offers AVIF support on newer versions.
