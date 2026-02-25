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
    encode { options { outputFormat = OutputFormat.Exact(ImageFormat.Bmp) } }
  }.transmute(inputBytes.asBytes()).bytes.data

// Decode BMP (re-encode to JPEG)
suspend fun decodeToJpeg(bmpBytes: ByteArray): ByteArray =
  Transmute.image {
    encode { options(JpegEncodeOptions(quality = 0.85f)) }
  }.transmute(bmpBytes.asBytes()).bytes.data
```

## Structure Reading

BMP files can be parsed into a `Bmp` structure that mirrors the file/DIB header layout:

```kotlin
val bmp: Bmp = Transmute.structure.read(bmpBytes.asBytes(), ImageFormat.Bmp)

// Access headers
val fileHeader = bmp.fileHeader   // 14-byte BMP file header
val dibHeader = bmp.dibHeader     // DIB header (BITMAPINFOHEADER / V4 / V5)
val pixelData = bmp.pixelData     // raw pixel bytes

// Round-trip
val raw = Transmute.structure.write(bmp)
```

The reader parses the file header, DIB header (including V4/V5 extensions), colour table, and pixel data. See `docs/structures.md`.

## Notes

- Very large files due to minimal compression.
- Includes a pure Kotlin codec that works on all platforms.
