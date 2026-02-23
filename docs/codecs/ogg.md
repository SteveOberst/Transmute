# OGG (Vorbis)

Ogg Vorbis is an open-source lossy audio format. It provides good quality at lower bitrates and is commonly used in games and open ecosystems.

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

// Convert audio to OGG (Vorbis) (Android/Desktop)
suspend fun convertToOgg(inputBytes: ByteArray): ByteArray =
  Transmute.audio {
    encode { options(CanonicalAudioEncodeOptions(outputFormat = OutputFormat.Exact(AudioFormat.Ogg))) }
  }.transmute(inputBytes.asBytes()).bytes.data

// Decode OGG on any platform (re-encode to WAV)
suspend fun decodeToWav(oggBytes: ByteArray): ByteArray =
  Transmute.audio {
    encode { options(CanonicalAudioEncodeOptions(outputFormat = OutputFormat.Exact(AudioFormat.Wav))) }
  }.transmute(oggBytes.asBytes()).bytes.data
```

## Notes

- Open and royalty-free.
- iOS can decode OGG but cannot encode to it.
- Desktop encoding relies on the bundled FFmpeg build.
