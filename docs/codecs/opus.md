# OPUS

Opus is a modern audio codec optimized for interactive speech and music over the internet. It delivers excellent quality across a wide range of bitrates and is widely used in VoIP and streaming.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅     | MediaCodec |
| Desktop  | ✅     | ✅     | GStreamer (requires `transmute-gstreamer`) |
| iOS      | ✅     | ❌     | AVFoundation (decode only) |

## Usage

```kotlin
// Convert audio to OPUS (Android/Desktop)
suspend fun convertToOpus(inputBytes: ByteArray): ByteArray =
  Transmute.audio {
    encode { options { outputFormat = OutputFormat.Exact(AudioFormat.Opus) } }
  }.transmute(inputBytes.asBytes()).bytes.data

// Decode OPUS on any platform (re-encode to WAV)
suspend fun decodeToWav(opusBytes: ByteArray): ByteArray =
  Transmute.audio {
    encode { options { outputFormat = OutputFormat.Exact(AudioFormat.Wav) } }
  }.transmute(opusBytes.asBytes()).bytes.data
```

## Structure Reading

Opus files can be parsed into an `Opus` structure that mirrors the Ogg container for Opus bitstreams:

```kotlin
val opus: Opus = Transmute.structure.read(opusBytes.asBytes(), AudioFormat.Opus)

// Round-trip
val raw = Transmute.structure.write(opus)
```

The reader validates the Ogg page capture pattern and the `OpusHead` / `OpusTags` identification
packets on the first page. See `docs/structures.md`.

## Notes
- Great for speech, music, and mixed content.
- Desktop requires the optional `transmute-gstreamer` module with GStreamer installed.
