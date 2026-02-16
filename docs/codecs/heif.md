# HEIF / HEIC

HEIF (High Efficiency Image File Format) and its HEIC variant use HEVC (H.265) compression to achieve significantly smaller file sizes than JPEG at equivalent quality. It is the default photo format on modern Apple devices.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ❌     | BitmapFactory (API 28+ decode) |
| Desktop  | ✅     | ✅     | FFmpeg (requires libx265) |
| iOS      | ✅     | ✅     | CoreGraphics / AVFoundation |

## Usage

```kotlin
// Convert to HEIF (Desktop/iOS only for encode)
val heifBytes = Transmute.image(inputBytes) {
    outputFormat(ImageFormat.HEIF)
    quality(0.85f)
}

// Decode HEIF to another format (all platforms)
val jpegBytes = Transmute.image(heifBytes) {
    outputFormat(ImageFormat.JPEG)
}
```

## Notes

- Android can **decode** HEIF (API 28+) but cannot encode to it.
- Desktop encoding requires the bundled FFmpeg to be built with `libx265`.
- iOS has full native support since iOS 11.
- HEIF supports alpha, depth maps, and multi-image sequences, though Transmute exposes single-image conversion only.
- May involve HEVC/H.265 patent licensing considerations for distribution.
