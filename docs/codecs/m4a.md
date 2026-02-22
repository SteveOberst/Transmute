# M4A (AAC in MP4)

M4A is an audio-only MP4 container, typically containing AAC audio. It is widely supported and commonly used by Apple ecosystems.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅     | MediaCodec |
| Desktop  | ✅     | ✅     | FFmpeg (bundled) |
| iOS      | ✅     | ✅     | AVFoundation |

## Usage

```kotlin
import dev.transmute.Transmute
import dev.transmute.audio.DefaultAudioEncodeOptions
import dev.transmute.core.AudioFormat

// Convert audio to M4A
suspend fun convertToM4a(inputBytes: ByteArray): ByteArray =
  Transmute.audio {
    encodeOptions(DefaultAudioEncodeOptions(outputFormat = AudioFormat.M4A))
  }.transmute(inputBytes).bytes

// Decode M4A (re-encode to WAV)
suspend fun decodeToWav(m4aBytes: ByteArray): ByteArray =
  Transmute.audio {
    encodeOptions(DefaultAudioEncodeOptions(outputFormat = AudioFormat.WAV))
  }.transmute(m4aBytes).bytes
```

## Notes

- M4A is a container; the codec is typically AAC.
- Great compatibility on iOS and modern Android.
- Desktop encoding relies on the bundled FFmpeg build.
