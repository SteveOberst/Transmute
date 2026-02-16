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
// Convert any image to PNG
val pngBytes = Transmute.image(inputBytes) {
    outputFormat(ImageFormat.PNG)
}

// quality() has no effect on PNG — compression is always lossless
```

## Notes

- Lossless compression — no quality degradation on re-encode.
- Supports full alpha transparency (RGBA).
- `quality()` parameter is ignored; PNG is always lossless.
- File sizes are typically larger than JPEG for photographic content.
- Universally supported across all platforms with no additional dependencies.
