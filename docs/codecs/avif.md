# AVIF

AVIF (AV1 Image File Format) is a next-generation image format based on the AV1 video codec. It offers excellent compression efficiency, often surpassing HEIF and WebP, and is royalty-free.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ❌     | BitmapFactory (API 31+ decode) |
| Desktop  | ✅     | ✅     | FFmpeg (requires libaom) |
| iOS      | ✅     | ✅     | CoreGraphics (iOS 16+) |

## Usage

```kotlin
// Convert to AVIF (Desktop/iOS only for encode)
val avifBytes = Transmute.image(inputBytes) {
    outputFormat(ImageFormat.AVIF)
    quality(0.80f)
}

// Decode AVIF to another format
val pngBytes = Transmute.image(avifBytes) {
    outputFormat(ImageFormat.PNG)
}
```

## Notes

- Android can **decode** AVIF on API 31+ but cannot encode to it.
- Desktop encoding requires the bundled FFmpeg with `libaom-av1` support.
- iOS has full AVIF support starting with iOS 16.
- Encoding can be noticeably slower than JPEG/WebP due to AV1 complexity.
- Royalty-free - no patent licensing concerns unlike HEIF.
- Supports HDR, wide color gamut, and alpha transparency.
