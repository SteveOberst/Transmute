# FLAC

FLAC (Free Lossless Audio Codec) is a lossless audio format. It compresses audio without any quality loss, making it ideal for archiving and high-fidelity audio.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅     | MediaCodec (decode) / FFmpeg (encode) |
| Desktop  | ✅     | ✅     | FFmpeg (bundled) |
| iOS      | ✅     | ❌     | AVFoundation (decode only) |

## Usage

```kotlin
import dev.transmute.Transmute
import dev.transmute.audio.CanonicalAudioEncodeOptions
import dev.transmute.core.OutputFormat
import dev.transmute.core.asBytes
import dev.transmute.audio.AudioFormat

// Convert audio to FLAC (Android/Desktop)
suspend fun convertToFlac(inputBytes: ByteArray): ByteArray =
  Transmute.audio {
    encode { options(CanonicalAudioEncodeOptions(outputFormat = OutputFormat.Exact(AudioFormat.Flac))) }
  }.transmute(inputBytes.asBytes()).bytes.data

// Decode FLAC on any platform (re-encode to WAV)
suspend fun decodeToWav(flacBytes: ByteArray): ByteArray =
  Transmute.audio {
    encode { options(CanonicalAudioEncodeOptions(outputFormat = OutputFormat.Exact(AudioFormat.Wav))) }
  }.transmute(flacBytes.asBytes()).bytes.data
```

## Notes

- Lossless compression - no quality degradation on re-encode.
- iOS can decode FLAC but cannot encode to it.
- Desktop encoding relies on the bundled FFmpeg build.
