# JPEG

JPEG (Joint Photographic Experts Group) is a widely-used lossy image compression format, ideal for photographs and complex images with smooth color gradients.

## Platform Support

| Platform | Decode | Encode | Engine                          |
|----------|--------|--------|---------------------------------|
| Android  | ✅      | ✅      | BitmapFactory / Bitmap.compress |
| Desktop  | ✅      | ✅      | ImageIO (javax.imageio)         |
| iOS      | ✅      | ✅      | CoreGraphics (CGImage)          |

## Usage

```kotlin
suspend fun convertToJpeg(inputBytes: ByteArray): ByteArray =
  Transmute.image {
    encode { options(JpegEncodeOptions(quality = 0.85f)) } // 0.0 - 1.0
  }.transmute(inputBytes.asBytes()).bytes.data

suspend fun compressMore(inputBytes: ByteArray): ByteArray =
  Transmute.image {
    encode { options(JpegEncodeOptions(quality = 0.5f)) }
  }.transmute(inputBytes.asBytes()).bytes.data
```

## Structure Reading

JPEG files can be parsed into a `Jpeg` structure that mirrors the segment layout:

```kotlin
val jpeg: Jpeg = Transmute.structure.read(jpegBytes.asBytes(), ImageFormat.Jpeg)

// Access segments (SOI, APP0, DQT, SOF, SOS, EOI, ...)
val segments = jpeg.segments // List<JpegSegment>

// Round-trip
val raw = Transmute.structure.write(jpeg)
```

The reader handles standalone markers, payload markers, and SOS entropy-coded data with byte-stuffing. See `docs/structures.md`.

## Notes

- Lossy compression - each re-encode degrades quality slightly.
- `quality` ranges from `0.0` (maximum compression) to `1.0` (best quality).
- Does not support transparency; alpha channels are flattened to white/black.
- EXIF metadata handling varies by platform.
- Universally supported across all platforms with no additional dependencies.
