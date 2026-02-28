# FLAC

FLAC (Free Lossless Audio Codec) is a lossless audio format. It compresses audio without any quality loss, making it ideal for archiving and high-fidelity audio.

## Platform Support

| Platform | Decode | Encode | Engine                                   |
|----------|--------|--------|------------------------------------------|
| Android  | ✅      | ✅      | MediaCodec                               |
| Desktop  | ✅      | ✅      | JFlac (decode) / GStreamer (+gst encode) |
| iOS      | ✅      | ❌      | AVFoundation (decode only)               |

## Usage

```kotlin
// Convert audio to FLAC (Android/Desktop)
suspend fun convertToFlac(inputBytes: ByteArray): ByteArray =
  Transmute.audio {
    encode { options { outputFormat = OutputFormat.Exact(AudioFormat.Flac) } }
  }.transmute(inputBytes.asBytes()).bytes.data

// Decode FLAC on any platform (re-encode to WAV)
suspend fun decodeToWav(flacBytes: ByteArray): ByteArray =
  Transmute.audio {
    encode { options { outputFormat = OutputFormat.Exact(AudioFormat.Wav) } }
  }.transmute(flacBytes.asBytes()).bytes.data
```

## Structure Reading

FLAC files can be parsed into a `Flac` structure that mirrors the metadata block layout:

```kotlin
val flac: Flac = Transmute.structure.read(flacBytes.asBytes(), AudioFormat.Flac)

// Access metadata blocks
val blocks = flac.metadataBlocks // List<FlacMetadataBlock>
val audioData = flac.audioData   // raw audio frames after the last metadata block

// Round-trip
val raw = Transmute.structure.write(flac)
```

The reader validates the "fLaC" magic bytes and parses each metadata block header (isLast flag, type, 24-bit length). See `docs/structures.md`.

## Notes

- Lossless compression - no quality degradation on re-encode.
- iOS can decode FLAC but cannot encode to it.
- Desktop decode is native via JFlac; encoding requires the optional `transmute-gstreamer` module.
