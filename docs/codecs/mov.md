# MOV (QuickTime)

MOV is Apple's QuickTime container format. It is structurally similar to MP4 and is the default video format for iOS/macOS recordings.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅     | MediaCodec |
| Desktop  | ✅     | ✅     | FFmpeg (bundled) |
| iOS      | ✅     | ✅     | AVFoundation / AVAssetWriter |

## Usage

```kotlin
// Convert video to MOV
val movBytes = Transmute.video(inputBytes) {
    outputFormat(VideoFormat.MOV)
}

// Convert MOV to MP4
val mp4Bytes = Transmute.video(movBytes) {
    outputFormat(VideoFormat.MP4)
}
```

## Notes

- Full encode + decode support on all platforms.
- MOV and MP4 share the same underlying ISO Base Media File Format (ISOBMFF).
- Android handles MOV via MediaCodec (treated similarly to MP4).
- Desktop uses the bundled FFmpeg for full support.
- iOS has native MOV support — it is the default capture format for the camera.
- Supports H.264, H.265/HEVC, and ProRes video codecs.
- Converting MOV ↔ MP4 is often a fast container remux with no re-encoding.
