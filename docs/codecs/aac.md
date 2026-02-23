# AAC

AAC (Advanced Audio Coding) is a modern lossy audio codec that provides better quality than MP3 at similar bitrates. It's the default audio codec used in MP4/M4A containers.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅     | MediaCodec |
| Desktop  | ✅     | ✅     | FFmpeg (bundled) |
| iOS      | ✅     | ✅     | AVFoundation |

## Usage

```kotlin
// Convert audio to AAC
suspend fun convertToAac(inputBytes: ByteArray): ByteArray =
  Transmute.audio {
    encode { options { outputFormat = OutputFormat.Exact(AudioFormat.Aac) } }
  }.transmute(inputBytes.asBytes()).bytes.data

// Decode AAC (re-encode to WAV)
suspend fun decodeToWav(aacBytes: ByteArray): ByteArray =
  Transmute.audio {
    encode { options { outputFormat = OutputFormat.Exact(AudioFormat.Wav) } }
  }.transmute(aacBytes.asBytes()).bytes.data
```

## Notes

- Great balance of quality and compression.
- Supported natively on Android and iOS.
- Typically used inside MP4/M4A containers.
- Desktop uses the bundled FFmpeg build.
