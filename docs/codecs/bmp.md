# BMP

BMP (Bitmap Image File) is an uncompressed raster image format. Transmute includes a **pure-Kotlin BMP encoder** that works identically across all platforms.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅     | BitmapFactory (decode) / Pure-Kotlin (encode) |
| Desktop  | ✅     | ✅     | ImageIO (decode) / Pure-Kotlin (encode) |
| iOS      | ✅     | ✅     | CoreGraphics (decode) / Pure-Kotlin (encode) |

## Usage

```kotlin
// Convert any image to BMP
val bmpBytes = Transmute.image(inputBytes) {
    outputFormat(ImageFormat.BMP)
}

// Decode BMP to another format
val jpegBytes = Transmute.image(bmpBytes) {
    outputFormat(ImageFormat.JPEG)
    quality(0.90f)
}
```

## Notes

- The BMP **encoder** is pure Kotlin - consistent behavior across all platforms.³
- Decoding uses each platform's native image decoder.
- BMP files are uncompressed and can be very large.
- `quality()` has no effect - BMP is always uncompressed.
- Does not support transparency in encoded output.
- Useful as an intermediate format when pixel-perfect fidelity is needed.

> ³ See project README for details on the pure-Kotlin BMP encoder.
