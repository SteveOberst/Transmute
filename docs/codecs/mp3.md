# MP3

MP3 (MPEG-1 Audio Layer III) is the most widely-used lossy audio compression format. It offers good compression ratios and universal playback support.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅     | MediaCodec (decode) / jump3r (encode) |
| Desktop  | ✅     | ✅     | JLayer (decode) / jump3r (encode) |
| iOS      | ✅     | ❌     | AVFoundation (decode only) |

## Usage

```kotlin
// Convert audio to MP3 (Android/Desktop)
suspend fun convertToMp3(inputBytes: ByteArray): ByteArray =
  Transmute.audio {
    encode { options { outputFormat = OutputFormat.Exact(AudioFormat.Mp3) } }
  }.transmute(inputBytes.asBytes()).bytes.data

// Decode MP3 on any platform (re-encode to WAV)
suspend fun decodeToWav(mp3Bytes: ByteArray): ByteArray =
  Transmute.audio {
    encode { options { outputFormat = OutputFormat.Exact(AudioFormat.Wav) } }
  }.transmute(mp3Bytes.asBytes()).bytes.data
```

## Notes

- iOS can **decode** MP3 but cannot encode to it.
- Android decoding uses the hardware-accelerated MediaCodec pipeline.
- Desktop decode uses JLayer; encode uses jump3r (pure-Java LAME port).
- Lossy compression - re-encoding degrades quality.
- Supports bitrates from 32 kbps to 320 kbps.
- The most universally compatible audio format across devices and players.
