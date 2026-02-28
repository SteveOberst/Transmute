# TIFF

TIFF is a flexible image container format used in professional imaging workflows. It can store high bit-depth and multiple pages, depending on the encoder/decoder.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ❌     | BitmapFactory (decode only) |
| Desktop  | ✅     | ✅     | ImageIO |
| iOS      | ✅     | ✅     | CoreGraphics (CGImage) |

## Usage

```kotlin
// Convert any image to TIFF (Desktop/iOS)
suspend fun convertToTiff(inputBytes: ByteArray): ByteArray =
  Transmute.image {
    encode { options { outputFormat = OutputFormat.Exact(ImageFormat.Tiff) } }
  }.transmute(inputBytes.asBytes()).bytes.data

// Decode TIFF (re-encode to JPEG)
suspend fun decodeToJpeg(tiffBytes: ByteArray): ByteArray =
  Transmute.image {
    encode { options(JpegEncodeOptions(quality = 0.85f)) }
  }.transmute(tiffBytes.asBytes()).bytes.data
```

## Structure Reading

TIFF files can be parsed into a `Tiff` structure that mirrors the IFD chain:

```kotlin
val tiff: Tiff = Transmute.structure.read(tiffBytes.asBytes(), ImageFormat.Tiff)

// Round-trip
val raw = Transmute.structure.write(tiff)
```

The reader auto-detects little-endian (II) and big-endian (MM) byte orders, then walks all
Image File Directories (IFDs) and their tag entries. See `docs/structures.md`.

## Notes features vary by platform codec implementation.
- Desktop uses ImageIO for TIFF encode/decode.
