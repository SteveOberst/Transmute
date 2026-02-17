# GIF

GIF (Graphics Interchange Format) is a legacy image format supporting lossless compression with a limited 256-color palette. It is best known for simple animations.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅     | BitmapFactory / Bitmap.compress |
| Desktop  | ✅     | ✅     | ImageIO (javax.imageio) |
| iOS      | ✅     | ✅     | CoreGraphics (CGImage) |

## Usage

```kotlin
// Convert an image to GIF
val gifBytes = Transmute.image(inputBytes) {
    outputFormat(ImageFormat.GIF)
}

// Decode a GIF to PNG
val pngBytes = Transmute.image(gifBytes) {
    outputFormat(ImageFormat.PNG)
}
```

## Notes

- Limited to 256 colors per frame - produces visible banding on photographic content.
- Encoding produces a **single-frame** GIF; animated GIF creation is not supported.
- Decoding animated GIFs extracts the first frame only.
- Supports binary (1-bit) transparency - no partial/alpha transparency.
- `quality()` has no meaningful effect on GIF output.
- Consider WebP or AVIF for better compression and quality.
