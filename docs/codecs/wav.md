# WAV

WAV (Waveform Audio File Format) is an uncompressed (or lightly compressed) audio format. It is simple, widely supported, and ideal as an intermediate format for transformations.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅     | Pure Kotlin / MediaCodec |
| Desktop  | ✅     | ✅     | Pure Kotlin |
| iOS      | ✅     | ✅     | Pure Kotlin / AVFoundation |

## Usage

```kotlin
// Convert any audio to WAV
suspend fun convertToWav(inputBytes: ByteArray): ByteArray =
  Transmute.audio {
    encode { options { outputFormat = OutputFormat.Exact(AudioFormat.Wav) } }
  }.transmute(inputBytes.asBytes()).bytes.data

// Convert WAV to AAC
suspend fun convertToAac(wavBytes: ByteArray): ByteArray =
  Transmute.audio {
    encode { options { outputFormat = OutputFormat.Exact(AudioFormat.Aac) } }
  }.transmute(wavBytes.asBytes()).bytes.data
```

## Structure Reading

WAV files can be parsed into a `Wav` structure that mirrors the RIFF container layout:

```kotlin
val wav: Wav = Transmute.structure.read(wavBytes.asBytes(), AudioFormat.Wav)

// Access RIFF container
val riff = wav.riffHeader             // RiffHeader (id, size, formType)
val children = riff.children          // List<RiffChunk>
val fmtChunk = children.firstOrNull { it.chunkId == "fmt " }

// Round-trip
val raw = Transmute.structure.write(wav)
```

The reader recursively parses RIFF/LIST containers and leaf chunks, handling odd-size pad bytes. See `docs/structures.md`.

## Notes

- Simple format, ideal for intermediate processing.
- Larger files due to minimal compression.
- Includes a pure Kotlin codec that works on all platforms.
