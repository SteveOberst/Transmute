# FLAC

FLAC (Free Lossless Audio Codec) is an open-source lossless audio compression format. It reduces file size by roughly 50–60% compared to WAV with no quality loss.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅     | MediaCodec |
| Desktop  | ✅     | ✅     | jflac-codec (decode) / FFmpeg (encode) |
| iOS      | ✅     | ✅     | AVFoundation / AVAssetWriter |

## Usage

```kotlin
// Convert audio to FLAC
val flacBytes = Transmute.audio(inputBytes) {
    outputFormat(AudioFormat.FLAC)
}

// Decode FLAC to WAV
val wavBytes = Transmute.audio(flacBytes) {
    outputFormat(AudioFormat.WAV)
}
```

## Notes

- Lossless compression - fully reversible to original PCM audio.
- Android uses MediaCodec for hardware-accelerated encode and decode.
- Desktop decoding uses jflac-codec (pure-Java); encoding uses the bundled FFmpeg.
- iOS encodes via AVAssetWriter with native support.
- Smaller than WAV but larger than lossy formats (MP3, AAC, OGG).
- Royalty-free and open-source.
