# WebP

WebP is a modern image format developed by Google that supports both lossy and lossless compression, along with alpha transparency. It typically achieves smaller file sizes than JPEG and PNG.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅     | BitmapFactory / Bitmap.compress |
| Desktop  | ✅     | ✅     | ImageIO (TwelveMonkeys / bundled codec) |
| iOS      | ✅     | ✅     | CoreGraphics (CGImage, iOS 14+) |

## Usage

```kotlin
// Lossy WebP conversion
val webpBytes = Transmute.image(inputBytes) {
    outputFormat(ImageFormat.WEBP)
    quality(0.80f)
}

// Lossless WebP (quality = 1.0)
val losslessWebp = Transmute.image(inputBytes) {
    outputFormat(ImageFormat.WEBP)
    quality(1.0f)
}
```

## Notes

- Supports both lossy and lossless modes; `quality(1.0f)` selects lossless on supported platforms.
- Supports alpha transparency in both lossy and lossless modes.
- Android has native WebP support since API 14 (lossy) and API 18 (lossless).
- Excellent compression-to-quality ratio for web-optimized images.
- Animated WebP is not currently supported by Transmute.
