# WAV

WAV (Waveform Audio File Format) is an uncompressed (or lightly compressed) audio format. It is simple, widely supported, and ideal as an intermediate format for transformations.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅     | Pure Kotlin / MediaCodec |
| Desktop  | ✅     | ✅     | Pure Kotlin |
| iOS      | ✅     | ✅     | Pure Kotlin / AVFoundation |

## Usage

```kotlin
import dev.transmute.Transmute
import dev.transmute.audio.DefaultAudioEncodeOptions
import dev.transmute.core.AudioFormat

// Convert any audio to WAV
suspend fun convertToWav(inputBytes: ByteArray): ByteArray =
  Transmute.audio {
    encodeOptions(DefaultAudioEncodeOptions(outputFormat = AudioFormat.WAV))
  }.transmute(inputBytes).bytes

// Convert WAV to AAC
suspend fun convertToAac(wavBytes: ByteArray): ByteArray =
  Transmute.audio {
    encodeOptions(DefaultAudioEncodeOptions(outputFormat = AudioFormat.AAC))
  }.transmute(wavBytes).bytes
```

## Notes

- Simple format, ideal for intermediate processing.
- Larger files due to minimal compression.
- Includes a pure Kotlin codec that works on all platforms.
