# OPUS

Opus is a modern audio codec optimized for interactive speech and music over the internet. It delivers excellent quality across a wide range of bitrates and is widely used in VoIP and streaming.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅     | MediaCodec (decode) / FFmpeg (encode) |
| Desktop  | ✅     | ✅     | FFmpeg (bundled) |
| iOS      | ✅     | ❌     | AVFoundation (decode only) |

## Usage

```kotlin
// Convert audio to OPUS (Android/Desktop)
suspend fun convertToOpus(inputBytes: ByteArray): ByteArray =
  Transmute.audio {
    encode { options(CanonicalAudioEncodeOptions(outputFormat = OutputFormat.Exact(AudioFormat.Opus))) }
  }.transmute(inputBytes.asBytes()).bytes.data

// Decode OPUS on any platform (re-encode to WAV)
suspend fun decodeToWav(opusBytes: ByteArray): ByteArray =
  Transmute.audio {
    encode { options(CanonicalAudioEncodeOptions(outputFormat = OutputFormat.Exact(AudioFormat.Wav))) }
  }.transmute(opusBytes.asBytes()).bytes.data
```

## Notes

- Excellent quality at low bitrates.
- Great for speech, music, and mixed content.
- Desktop encoding relies on the bundled FFmpeg build.
