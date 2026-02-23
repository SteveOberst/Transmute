# PNG

PNG (Portable Network Graphics) is a lossless image format that supports transparency (alpha channel). Best for graphics, screenshots, and images requiring pixel-perfect fidelity.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅     | BitmapFactory / Bitmap.compress |
| Desktop  | ✅     | ✅     | ImageIO (javax.imageio) |
| iOS      | ✅     | ✅     | CoreGraphics (CGImage) |

## Usage

```kotlin
suspend fun convertToPng(inputBytes: ByteArray): ByteArray =
  Transmute.image {
    encode { options(PngEncodeOptions()) }
  }.transmute(inputBytes.asBytes()).bytes.data
```

## Notes

- Lossless compression - no quality degradation on re-encode.
- Supports full alpha transparency (RGBA).
- File sizes are typically larger than JPEG for photographic content.
- Universally supported across all platforms with no additional dependencies.
