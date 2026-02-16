# AAC

AAC (Advanced Audio Coding) is a lossy audio format that provides better sound quality than MP3 at similar bitrates. It is the default audio codec for iOS, YouTube, and most streaming services.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅     | MediaCodec |
| Desktop  | ✅     | ✅     | FFmpeg (bundled) |
| iOS      | ✅     | ✅     | AVFoundation / AVAssetWriter |

## Usage

```kotlin
// Convert audio to AAC
val aacBytes = Transmute.audio(inputBytes) {
    outputFormat(AudioFormat.AAC)
}

// Decode AAC to WAV
val wavBytes = Transmute.audio(aacBytes) {
    outputFormat(AudioFormat.WAV)
}
```

## Notes

- Full encode + decode support on all platforms.
- Android uses hardware-accelerated MediaCodec for both encoding and decoding.
- Desktop relies on the bundled FFmpeg — no user setup needed.
- iOS encodes via AVAssetWriter with native hardware acceleration.
- Lossy compression — typically superior to MP3 at equivalent bitrates.
- AAC output is commonly wrapped in an M4A/MP4 container.
