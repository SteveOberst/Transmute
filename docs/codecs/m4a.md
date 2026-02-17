# M4A

M4A is an audio-only MPEG-4 container, typically containing AAC or ALAC encoded audio. It is the standard audio format for Apple ecosystem content.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅     | MediaCodec |
| Desktop  | ✅     | ✅     | FFmpeg (bundled) |
| iOS      | ✅     | ✅     | AVFoundation / AVAssetWriter |

## Usage

```kotlin
// Convert audio to M4A
val m4aBytes = Transmute.audio(inputBytes) {
    outputFormat(AudioFormat.M4A)
}

// Decode M4A to WAV
val wavBytes = Transmute.audio(m4aBytes) {
    outputFormat(AudioFormat.WAV)
}
```

## Notes

- Full encode + decode support on all platforms.
- M4A is essentially an MP4 container with audio-only content (typically AAC).
- Android uses hardware-accelerated MediaCodec.
- Desktop relies on the bundled FFmpeg - no user setup needed.
- iOS has native support via AVFoundation / AVAssetWriter.
- Preferred over raw AAC when metadata (tags, album art) is needed.
- Functionally equivalent to AAC in most conversion scenarios.
