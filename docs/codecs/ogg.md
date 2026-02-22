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
import dev.transmute.audio.DefaultAudioEncodeOptions
import dev.transmute.core.AudioFormat

// Convert audio to OGG (Vorbis) (Android/Desktop)
suspend fun convertToOgg(inputBytes: ByteArray): ByteArray =
  Transmute.audio {
    encodeOptions(DefaultAudioEncodeOptions(outputFormat = AudioFormat.OGG))
  }.transmute(inputBytes).bytes

// Decode OGG on any platform (re-encode to WAV)
suspend fun decodeToWav(oggBytes: ByteArray): ByteArray =
  Transmute.audio {
    encodeOptions(DefaultAudioEncodeOptions(outputFormat = AudioFormat.WAV))
  }.transmute(oggBytes).bytes
```

## Notes

- Open and royalty-free.
- iOS can decode OGG but cannot encode to it.
- Desktop encoding relies on the bundled FFmpeg build.
