# TIFF

TIFF (Tagged Image File Format) is a flexible, lossless image format commonly used in professional photography and publishing workflows.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ❌     | BitmapFactory (decode only) |
| Desktop  | ✅     | ✅     | ImageIO + TwelveMonkeys plugin |
| iOS      | ✅     | ✅     | CoreGraphics (CGImage) |

## Usage

```kotlin
// Convert to TIFF (Desktop/iOS only for encode)
val tiffBytes = Transmute.image(inputBytes) {
    outputFormat(ImageFormat.TIFF)
}

// Decode TIFF on any platform
val jpegBytes = Transmute.image(tiffBytes) {
    outputFormat(ImageFormat.JPEG)
    quality(0.90f)
}
```

## Notes

- Android can **decode** TIFF but cannot encode to it.
- Desktop uses the TwelveMonkeys ImageIO plugin for full TIFF read/write support.
- iOS has full native TIFF support via CoreGraphics.
- TIFF supports multiple compression modes (LZW, ZIP, None), but Transmute uses platform defaults.
- File sizes are typically large; not recommended for general-purpose use.
- Supports alpha transparency and high bit-depth color.
