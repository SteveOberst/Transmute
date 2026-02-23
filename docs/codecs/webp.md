# WebP

WebP is a modern image format that supports both lossy and lossless compression, as well as alpha transparency. It often produces smaller files than JPEG/PNG at similar quality.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅     | BitmapFactory / Bitmap.compress |
| Desktop  | ✅     | ✅     | ImageIO (via plugins) / FFmpeg fallback |
| iOS      | ✅     | ✅     | CoreGraphics (CGImage) |

## Usage

```kotlin
import dev.transmute.Transmute
import dev.transmute.core.asBytes
import dev.transmute.image.WebPEncodeOptions

// Convert any image to WebP (lossy)
suspend fun convertToWebp(inputBytes: ByteArray): ByteArray =
  Transmute.image {
    encode { options(WebPEncodeOptions(quality = 0.8f, lossless = false)) }
  }.transmute(inputBytes.asBytes()).bytes.data

// Convert any image to WebP (lossless)
suspend fun convertToWebpLossless(inputBytes: ByteArray): ByteArray =
  Transmute.image {
    encode { options(WebPEncodeOptions(lossless = true)) }
  }.transmute(inputBytes.asBytes()).bytes.data
```

## Notes

- Great compression for both photos (lossy) and graphics (lossless).
- Supports alpha transparency.
- Widely supported on Android; good support on iOS and modern desktop workflows.
