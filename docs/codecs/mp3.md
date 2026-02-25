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

## Structure Reading

MP3 files can be parsed into an `Mp3` structure that captures ID3 tags and the audio frame data:

```kotlin
val mp3: Mp3 = Transmute.structure.read(mp3Bytes.asBytes(), AudioFormat.Mp3)

// Access top-level parts
val id3v2 = mp3.id3v2Tag   // optional ID3v2 header + tag data
val frames = mp3.audioData // raw audio frames as a single blob
val id3v1 = mp3.id3v1Tag   // optional 128-byte ID3v1 trailer

// Round-trip
val raw = Transmute.structure.write(mp3)
```

The reader extracts the optional ID3v2 tag (syncsafe integer size), raw audio frames, and optional ID3v1 trailer. See `docs/structures.md`.

## Notes

- iOS can **decode** MP3 but cannot encode to it.
- Android decoding uses the hardware-accelerated MediaCodec pipeline.
- Desktop decode uses JLayer; encode uses jump3r (pure-Java LAME port).
- Lossy compression - re-encoding degrades quality.
- Supports bitrates from 32 kbps to 320 kbps.
- The most universally compatible audio format across devices and players.
