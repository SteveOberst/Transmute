# BMP

BMP (Bitmap) is a simple, mostly uncompressed image format. It is large but easy to decode and encode, making it useful for debugging and interoperability with legacy systems.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅     | Pure Kotlin |
| Desktop  | ✅     | ✅     | Pure Kotlin |
| iOS      | ✅     | ✅     | Pure Kotlin |

## Usage

```kotlin
// Convert any image to BMP
suspend fun convertToBmp(inputBytes: ByteArray): ByteArray =
  Transmute.image {
    encode { options(CanonicalImageEncodeOptions(outputFormat = OutputFormat.Exact(ImageFormat.Bmp))) }
  }.transmute(inputBytes.asBytes()).bytes.data

// Decode BMP (re-encode to JPEG)
suspend fun decodeToJpeg(bmpBytes: ByteArray): ByteArray =
  Transmute.image {
    encode { options(JpegEncodeOptions(quality = 0.85f)) }
  }.transmute(bmpBytes.asBytes()).bytes.data
```

## Notes

- Very large files due to minimal compression.
- Includes a pure Kotlin codec that works on all platforms.
