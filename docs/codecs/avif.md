# AVIF

AVIF is a next-generation image format based on AV1. It provides excellent compression efficiency and high quality, especially for photographic content.

## Platform Support

| Platform | Decode | Encode | Engine                                     |
|----------|--------|--------|--------------------------------------------|
| Android  | ✅      | ❌      | ImageDecoder (decode only)                 |
| Desktop  | ✅      | ✅      | libheif (requires `transmute-libheif`)     |
| iOS      | ✅      | ✅      | CoreGraphics (CGImage)                     |

## Usage

```kotlin
// Convert any image to AVIF (Desktop/iOS)
suspend fun convertToAvif(inputBytes: ByteArray): ByteArray =
  Transmute.image {
    encode { options(HeifEncodeOptions(format = ImageFormat.Avif, quality = 0.8f)) }
  }.transmute(inputBytes.asBytes()).bytes.data

// Decode AVIF (re-encode to PNG)
suspend fun decodeToPng(avifBytes: ByteArray): ByteArray =
  Transmute.image {
    encode { options(PngEncodeOptions()) }
  }.transmute(avifBytes.asBytes()).bytes.data
```

## Structure Reading

AVIF files can be parsed into an `Avif` structure that mirrors the ISO BMFF box layout:

```kotlin
val avif: Avif = Transmute.structure.read(avifBytes.asBytes(), ImageFormat.Avif)

// Round-trip
val raw = Transmute.structure.write(avif)
```

The reader walks `ftyp`, `meta`, and `mdat` boxes. `ftyp.majorBrand` is typically `"avif"` or `"avis"` for
AVIF sequences. See `docs/structures.md`.

## Notes encoding support is limited.
- Desktop requires the optional `transmute-libheif` module.
- iOS offers AVIF support on newer versions.
