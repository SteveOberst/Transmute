# JPEG

JPEG (Joint Photographic Experts Group) is a widely-used lossy image compression format, ideal for photographs and complex images with smooth color gradients.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅     | BitmapFactory / Bitmap.compress |
| Desktop  | ✅     | ✅     | ImageIO (javax.imageio) |
| iOS      | ✅     | ✅     | CoreGraphics (CGImage) |

## Usage

```kotlin
import dev.transmute.Transmute
import dev.transmute.image.JpegEncodeOptions

suspend fun convertToJpeg(inputBytes: ByteArray): ByteArray =
  Transmute.image {
    encodeOptions(JpegEncodeOptions(quality = 0.85f)) // 0.0 – 1.0
  }.transmute(inputBytes).bytes

suspend fun compressMore(inputBytes: ByteArray): ByteArray =
  Transmute.image {
    encodeOptions(JpegEncodeOptions(quality = 0.5f))
  }.transmute(inputBytes).bytes
```

## Notes

- Lossy compression - each re-encode degrades quality slightly.
- `quality` ranges from `0.0` (maximum compression) to `1.0` (best quality).
- Does not support transparency; alpha channels are flattened to white/black.
- EXIF metadata handling varies by platform.
- Universally supported across all platforms with no additional dependencies.
