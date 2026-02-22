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
import dev.transmute.Transmute
import dev.transmute.audio.DefaultAudioEncodeOptions
import dev.transmute.core.AudioFormat

// Convert audio to OPUS (Android/Desktop)
suspend fun convertToOpus(inputBytes: ByteArray): ByteArray =
  Transmute.audio {
    encodeOptions(DefaultAudioEncodeOptions(outputFormat = AudioFormat.OPUS))
  }.transmute(inputBytes).bytes

// Decode OPUS on any platform (re-encode to WAV)
suspend fun decodeToWav(opusBytes: ByteArray): ByteArray =
  Transmute.audio {
    encodeOptions(DefaultAudioEncodeOptions(outputFormat = AudioFormat.WAV))
  }.transmute(opusBytes).bytes
```

## Notes

- Excellent quality at low bitrates.
- Great for speech, music, and mixed content.
- Desktop encoding relies on the bundled FFmpeg build.
