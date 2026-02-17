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
val mp3Bytes = Transmute.audio(inputBytes) {
    outputFormat(AudioFormat.MP3)
}

// Decode MP3 on any platform
val wavBytes = Transmute.audio(mp3Bytes) {
    outputFormat(AudioFormat.WAV)
}
```

## Notes

- iOS can **decode** MP3 but cannot encode to it.
- Android decoding uses the hardware-accelerated MediaCodec pipeline.
- Desktop decode uses JLayer; encode uses jump3r (pure-Java LAME port).
- Lossy compression - re-encoding degrades quality.
- Supports bitrates from 32 kbps to 320 kbps.
- The most universally compatible audio format across devices and players.
