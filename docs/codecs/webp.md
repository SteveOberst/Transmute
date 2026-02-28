# WebP

WebP is a modern image format that supports both lossy and lossless compression, as well as alpha transparency. It often produces smaller files than JPEG/PNG at similar quality.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅     | BitmapFactory / Bitmap.compress |
| Desktop  | ✅     | ✅     | ImageIO (via plugins) |
| iOS      | ✅     | ✅     | CoreGraphics (CGImage) |

## Usage

```kotlin
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

## Structure Reading

WebP files can be parsed into a `Webp` structure that mirrors the RIFF container layout:

```kotlin
val webp: Webp = Transmute.structure.read(webpBytes.asBytes(), ImageFormat.Webp)

// Round-trip
val raw = Transmute.structure.write(webp)
```

The reader parses the outer RIFF/WEBP wrapper, then the VP8 / VP8L / VP8X sub-chunk and optional
extended chunks (color profile, animation, metadata). See `docs/structures.md`.

## Notes
- Supports alpha transparency.
- Widely supported on Android; good support on iOS and modern desktop workflows.
