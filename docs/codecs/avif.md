# AVIF

AVIF is a next-generation image format based on AV1. It provides excellent compression efficiency and high quality, especially for photographic content.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ❌     | ImageDecoder (decode only) |
| Desktop  | ✅     | ✅     | FFmpeg (bundled) |
| iOS      | ✅     | ✅     | CoreGraphics (CGImage) |

## Usage

```kotlin
import dev.transmute.Transmute
import dev.transmute.core.ImageFormat
import dev.transmute.image.HeifEncodeOptions
import dev.transmute.image.PngEncodeOptions

// Convert any image to AVIF (Desktop/iOS)
suspend fun convertToAvif(inputBytes: ByteArray): ByteArray =
  Transmute.image {
    encodeOptions(HeifEncodeOptions(outputFormat = ImageFormat.AVIF, quality = 0.8f))
  }.transmute(inputBytes).bytes

// Decode AVIF (re-encode to PNG)
suspend fun decodeToPng(avifBytes: ByteArray): ByteArray =
  Transmute.image {
    encodeOptions(PngEncodeOptions())
  }.transmute(avifBytes).bytes
```

## Notes

- Android decode support depends on OS version; encoding support is limited.
- Desktop encoding relies on the bundled FFmpeg build.
- iOS offers AVIF support on newer versions.
