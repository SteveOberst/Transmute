# HEIF/HEIC

HEIF/HEIC are modern image formats used heavily in Apple ecosystems. They offer excellent compression efficiency while maintaining high visual quality.

## Platform Support

| Platform | Decode | Encode | Engine                                     |
|----------|--------|--------|--------------------------------------------|
| Android  | ✅      | ❌      | BitmapFactory (decode only)                |
| Desktop  | ✅      | ✅      | libheif (requires `transmute-libheif`)     |
| iOS      | ✅      | ✅      | CoreGraphics (CGImage)                     |

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

## Structure Reading

HEIF/HEIC files can be parsed into a `Heif` structure that mirrors the ISO BMFF box layout:

```kotlin
val heif: Heif = Transmute.structure.read(heifBytes.asBytes(), ImageFormat.Heif)

// Round-trip
val raw = Transmute.structure.write(heif)
```

The reader walks the `ftyp`, `meta`, `mdat`, and `moov`/`hdlr` boxes, capturing all atoms at top-level
resolution. See `docs/structures.md`.

## Notes but encode support is limited.
- Desktop requires the optional `transmute-libheif` module.
- iOS offers strong native HEIF/HEIC support.
