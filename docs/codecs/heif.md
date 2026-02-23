# HEIF/HEIC

HEIF/HEIC are modern image formats used heavily in Apple ecosystems. They offer excellent compression efficiency while maintaining high visual quality.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ❌     | BitmapFactory (decode only) |
| Desktop  | ✅     | ✅     | FFmpeg (bundled) |
| iOS      | ✅     | ✅     | CoreGraphics (CGImage) |

## Usage

```kotlin
// Convert any image to HEIF (Desktop/iOS)
suspend fun convertToHeif(inputBytes: ByteArray): ByteArray =
  Transmute.image {
    encode { options(HeifEncodeOptions(format = ImageFormat.Heif, quality = 0.8f)) }
  }.transmute(inputBytes.asBytes()).bytes.data

// Decode HEIF (re-encode to JPEG)
suspend fun decodeToJpeg(heifBytes: ByteArray): ByteArray =
  Transmute.image {
    encode { options(JpegEncodeOptions(quality = 0.85f)) }
  }.transmute(heifBytes.asBytes()).bytes.data
```

## Notes

- Android can decode HEIF/HEIC on modern devices but encode support is limited.
- Desktop encoding relies on the bundled FFmpeg build.
- iOS offers strong native HEIF/HEIC support.
