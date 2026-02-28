# OGG (Vorbis)

Ogg Vorbis is an open-source lossy audio format. It provides good quality at lower bitrates and is commonly used in games and open ecosystems.

## Platform Support

| Platform | Decode | Encode | Engine                                     |
|----------|--------|--------|--------------------------------------------|
| Android  | ✅      | ✅      | MediaCodec                                 |
| Desktop  | ✅      | ✅      | GStreamer (requires `transmute-gstreamer`) |
| iOS      | ✅      | ❌      | AVFoundation (decode only)                 |

## Usage

```kotlin
// Convert audio to OGG (Vorbis) (Android/Desktop)
suspend fun convertToOgg(inputBytes: ByteArray): ByteArray =
  Transmute.audio {
    encode { options { outputFormat = OutputFormat.Exact(AudioFormat.Ogg) } }
  }.transmute(inputBytes.asBytes()).bytes.data

// Decode OGG on any platform (re-encode to WAV)
suspend fun decodeToWav(oggBytes: ByteArray): ByteArray =
  Transmute.audio {
    encode { options { outputFormat = OutputFormat.Exact(AudioFormat.Wav) } }
  }.transmute(oggBytes.asBytes()).bytes.data
```

## Structure Reading

OGG Vorbis files can be parsed into an `OggAudio` structure that mirrors the Ogg bitstream layout:

```kotlin
val ogg: OggAudio = Transmute.structure.read(oggBytes.asBytes(), AudioFormat.Ogg)

// Round-trip
val raw = Transmute.structure.write(ogg)
```

The reader parses Ogg page headers (capture pattern, stream serial number, page sequence number)
and collects logical bitstream packets. See `docs/structures.md`.

## Notes
- iOS can decode OGG but cannot encode to it.
- Desktop requires the optional `transmute-gstreamer` module with GStreamer installed.
