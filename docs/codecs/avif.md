# AVIF

AVIF is an AV1-based still-image format stored in an ISO BMFF container. Transmute can inspect it everywhere and decode or encode it where the platform or an installed plugin exposes codec support.

## Platform Support

| Platform | Decode | Encode | Notes |
|----------|--------|--------|-------|
| Android  | built-in | no | Platform decode only |
| Desktop  | plugin: libheif | plugin: libheif | Requires `transmute-plugins-libheif` |
| iOS      | built-in | built-in | CoreGraphics/ImageIO |

## Usage

```kotlin
// Convert any image to AVIF on a platform with AVIF encode support
suspend fun convertToAvif(inputBytes: ByteArray): ByteArray =
  transmute().image {
    encode {
      params(
        Params.of(
          ImageParamKeys.OutputFormat to OutputFormat.Exact(ImageFormat.Avif),
          ImageParamKeys.HeifQuality to 0.8f,
        ),
      )
    }
  }.transmute(inputBytes.asBytes()).bytes.data

// Decode AVIF (re-encode to PNG)
suspend fun decodeToPng(avifBytes: ByteArray): ByteArray =
  transmute().image.to(ImageFormat.Png).transmute(avifBytes.asBytes()).bytes.data
```

## Structure Reading

AVIF files can be parsed into an `AvifStructure` that mirrors the ISO BMFF box layout:

```kotlin
val structure = transmute().inspect.structure(avifBytes.asBytes(), ImageFormat.Avif)
if (structure is AvifStructure) {
    println("FTYP: ${structure.ftyp}")
    println("Boxes: ${structure.boxes.size}")
}
```

The reader walks `ftyp`, `meta`, and `mdat` boxes. `ftyp.majorBrand` is typically `"avif"` or `"avis"` for
AVIF sequences. See [structures.md](../structures.md).

## Notes

- Desktop requires the optional `transmute-plugins-libheif` module.
- Android provides built-in decode support only.
- iOS provides native decode and encode support on supported OS versions.



