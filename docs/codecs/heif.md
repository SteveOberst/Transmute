# HEIF/HEIC

HEIF and HEIC are ISO BMFF-based image formats used heavily by Apple platforms. Transmute can inspect them everywhere and decode or encode them where the platform or an installed plugin exposes codec support.

## Platform Support

| Platform | Decode | Encode | Notes |
|----------|--------|--------|-------|
| Android  | built-in | no | Platform decode only |
| Desktop  | plugin: libheif | plugin: libheif | Requires `transmute-plugins-libheif` |
| iOS      | built-in | built-in | CoreGraphics/ImageIO |

## Usage

```kotlin
// Convert any image to HEIF on a platform with HEIF encode support
suspend fun convertToHeif(inputBytes: ByteArray): ByteArray =
  transmute().image {
    encode {
      params(
        Params.of(
          ImageParamKeys.OutputFormat to OutputFormat.Exact(ImageFormat.Heif),
          ImageParamKeys.HeifQuality to 0.8f,
        ),
      )
    }
  }.transmute(inputBytes.asBytes()).bytes.data

// Decode HEIF (re-encode to JPEG)
suspend fun decodeToJpeg(heifBytes: ByteArray): ByteArray =
  transmute().image.to(ImageFormat.Jpeg) {
    encode {
      params(Params.of(ImageParamKeys.JpegQuality to 0.85f))
    }
  }.transmute(heifBytes.asBytes()).bytes.data
```

## Structure Reading

HEIF/HEIC files can be parsed into a `HeifStructure` that mirrors the ISO BMFF box layout:

```kotlin
val structure = transmute().inspect.structure(heifBytes.asBytes(), ImageFormat.Heif)
if (structure is HeifStructure) {
    println("FTYP: ${structure.ftyp}")
    println("Boxes: ${structure.boxes.size}")
}
```

The reader walks the `ftyp`, `meta`, `mdat`, and `moov`/`hdlr` boxes, capturing all atoms at top-level
resolution. See [structures.md](../structures.md).

## Notes

- Desktop requires the optional `transmute-plugins-libheif` module.
- Android provides built-in decode support only.
- iOS provides native decode and encode support.



