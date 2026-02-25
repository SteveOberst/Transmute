# PNG

PNG (Portable Network Graphics) is a lossless image format that supports transparency (alpha channel). Best for graphics, screenshots, and images requiring pixel-perfect fidelity.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅     | BitmapFactory / Bitmap.compress |
| Desktop  | ✅     | ✅     | ImageIO (javax.imageio) |
| iOS      | ✅     | ✅     | CoreGraphics (CGImage) |

## Usage

```kotlin
suspend fun convertToPng(inputBytes: ByteArray): ByteArray =
  Transmute.image {
    encode { options(PngEncodeOptions()) }
  }.transmute(inputBytes.asBytes()).bytes.data
```

## Structure Reading

PNG files can be parsed into a `Png` structure that mirrors the on-disk chunk layout:

```kotlin
val png: Png = Transmute.structure.read(pngBytes.asBytes(), ImageFormat.Png)

// Access chunks (IHDR, PLTE, IDAT, IEND, ...)
val chunks = png.chunks // List<PngChunk>

// Round-trip
val raw = Transmute.structure.write(png)
```

The reader validates the 8-byte PNG signature and parses each chunk (length, type, data, CRC). See `docs/structures.md`.

## Notes

- Lossless compression - no quality degradation on re-encode.
- Supports full alpha transparency (RGBA).
- File sizes are typically larger than JPEG for photographic content.
- Universally supported across all platforms with no additional dependencies.
