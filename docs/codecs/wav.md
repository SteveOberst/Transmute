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
// Convert any audio to WAV
suspend fun convertToWav(inputBytes: ByteArray): ByteArray =
  Transmute.audio {
    encode { options(CanonicalAudioEncodeOptions(outputFormat = OutputFormat.Exact(AudioFormat.Wav))) }
  }.transmute(inputBytes.asBytes()).bytes.data

// Convert WAV to AAC
suspend fun convertToAac(wavBytes: ByteArray): ByteArray =
  Transmute.audio {
    encode { options(CanonicalAudioEncodeOptions(outputFormat = OutputFormat.Exact(AudioFormat.Aac))) }
  }.transmute(wavBytes.asBytes()).bytes.data
```

## Notes

- Simple format, ideal for intermediate processing.
- Larger files due to minimal compression.
- Includes a pure Kotlin codec that works on all platforms.
