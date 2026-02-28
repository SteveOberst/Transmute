# M4A (AAC in MP4)

M4A is an audio-only MP4 container, typically containing AAC audio. It is widely supported and commonly used by Apple ecosystems.

## Platform Support

| Platform | Decode | Encode | Engine                                     |
|----------|--------|--------|--------------------------------------------|
| Android  | ✅      | ✅      | MediaCodec                                 |
| Desktop  | ✅      | ✅      | GStreamer (requires `transmute-gstreamer`) |
| iOS      | ✅      | ✅      | AVFoundation                               |

## Usage

```kotlin
// Convert audio to M4A
suspend fun convertToM4a(inputBytes: ByteArray): ByteArray =
  Transmute.audio {
    encode { options { outputFormat = OutputFormat.Exact(AudioFormat.M4a) } }
  }.transmute(inputBytes.asBytes()).bytes.data

// Decode M4A (re-encode to WAV)
suspend fun decodeToWav(m4aBytes: ByteArray): ByteArray =
  Transmute.audio {
    encode { options { outputFormat = OutputFormat.Exact(AudioFormat.Wav) } }
  }.transmute(m4aBytes.asBytes()).bytes.data
```

## Structure Reading

M4A files can be parsed into an `M4a` structure that mirrors the ISO BMFF box layout:

```kotlin
val m4a: M4a = Transmute.structure.read(m4aBytes.asBytes(), AudioFormat.M4a)

// Round-trip
val raw = Transmute.structure.write(m4a)
```

The reader walks `ftyp`, `moov`, `mdat`, and nested boxes (`trak`, `mdia`, `hdlr`, etc.).
`ftyp.majorBrand` is typically `"M4A "` or `"isom"`. See `docs/structures.md`.

## Notes
- Great compatibility on iOS and modern Android.
- Desktop requires the optional `transmute-gstreamer` module with GStreamer installed.
