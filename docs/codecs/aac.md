# AAC

AAC (Advanced Audio Coding) is a modern lossy audio codec that provides better quality than MP3 at similar bitrates. It's the default audio codec used in MP4/M4A containers.

## Platform Support

| Platform | Decode | Encode | Engine                                     |
|----------|--------|--------|--------------------------------------------|
| Android  | ✅      | ✅      | MediaCodec                                 |
| Desktop  | ✅      | ✅      | GStreamer (requires `transmute-gstreamer`) |
| iOS      | ✅      | ✅      | AVFoundation                               |

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

## Structure Reading

AAC files in ADTS format can be parsed into an `Aac` structure that captures the ADTS frame headers:

```kotlin
val aac: Aac = Transmute.structure.read(aacBytes.asBytes(), AudioFormat.Aac)

// Round-trip
val raw = Transmute.structure.write(aac)
```

The reader validates the 12-bit ADTS sync word (0xFFF) and parses per-frame header fields
(profile, sample rate index, channel config, frame length). See `docs/structures.md`.

## Notes
- Supported natively on Android and iOS.
- Typically used inside MP4/M4A containers.
- Desktop requires the optional `transmute-gstreamer` module with GStreamer installed.
