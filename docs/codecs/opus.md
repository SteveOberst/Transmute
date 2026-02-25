# OPUS

Opus is a modern audio codec optimized for interactive speech and music over the internet. It delivers excellent quality across a wide range of bitrates and is widely used in VoIP and streaming.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅     | MediaCodec |
| Desktop  | ✅     | ✅     | GStreamer (requires `transmute-gstreamer`) |
| iOS      | ✅     | ❌     | AVFoundation (decode only) |

## Usage

```kotlin
// Convert audio to OPUS (Android/Desktop)
suspend fun convertToOpus(inputBytes: ByteArray): ByteArray =
  Transmute.audio {
    encode { options { outputFormat = OutputFormat.Exact(AudioFormat.Opus) } }
  }.transmute(inputBytes.asBytes()).bytes.data

// Decode OPUS on any platform (re-encode to WAV)
suspend fun decodeToWav(opusBytes: ByteArray): ByteArray =
  Transmute.audio {
    encode { options { outputFormat = OutputFormat.Exact(AudioFormat.Wav) } }
  }.transmute(opusBytes.asBytes()).bytes.data
```

## Notes

- Excellent quality at low bitrates.
- Great for speech, music, and mixed content.
- Desktop requires the optional `transmute-gstreamer` module with GStreamer installed.
