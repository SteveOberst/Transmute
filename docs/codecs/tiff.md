# TIFF

TIFF is a flexible image container format used in professional imaging workflows. It can store high bit-depth and multiple pages, depending on the encoder/decoder.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ❌     | BitmapFactory (decode only) |
| Desktop  | ✅     | ✅     | FFmpeg (bundled) / ImageIO |
| iOS      | ✅     | ✅     | CoreGraphics (CGImage) |

## Usage

```kotlin
import dev.transmute.Transmute
import dev.transmute.core.ImageFormat
import dev.transmute.core.OutputFormat
import dev.transmute.image.CanonicalImageEncodeOptions
import dev.transmute.image.JpegEncodeOptions

// Convert any image to TIFF (Desktop/iOS)
suspend fun convertToTiff(inputBytes: ByteArray): ByteArray =
  Transmute.image {
    encodeOptions(CanonicalImageEncodeOptions(outputFormat = OutputFormat.Exact(ImageFormat.TIFF)))
  }.transmute(inputBytes).bytes

// Decode TIFF (re-encode to JPEG)
suspend fun decodeToJpeg(tiffBytes: ByteArray): ByteArray =
  Transmute.image {
    encodeOptions(JpegEncodeOptions(quality = 0.85f))
  }.transmute(tiffBytes).bytes
```

## Notes

- Common in professional workflows; features vary by platform codec implementation.
- Desktop encoding may rely on the bundled FFmpeg build.
