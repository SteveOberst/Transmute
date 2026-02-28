# AVI

AVI is a legacy video container format. It is still encountered frequently in archives and older workflows.

## Platform Support

| Platform | Decode | Encode | Engine                                     |
|----------|--------|--------|--------------------------------------------|
| Android  | ✅      | ✅      | MediaCodec                                 |
| Desktop  | ✅      | ✅      | GStreamer (requires `transmute-gstreamer`) |
| iOS      | ✅      | ❌      | AVFoundation (decode only)                 |

## Usage

```kotlin
// Convert video to AVI (Android/Desktop)
suspend fun convertToAvi(inputBytes: ByteArray): ByteArray =
  Transmute.video {
    encode { options { outputFormat = OutputFormat.Exact(VideoFormat.Avi) } }
  }.transmute(inputBytes.asBytes()).bytes.data

// Decode AVI (re-encode to MP4)
suspend fun convertToMp4(aviBytes: ByteArray): ByteArray =
  Transmute.video {
    encode { options { outputFormat = OutputFormat.Exact(VideoFormat.Mp4) } }
  }.transmute(aviBytes.asBytes()).bytes.data
```

## Structure Reading

AVI files can be parsed into an `Avi` structure that mirrors the RIFF container layout:

```kotlin
val avi: Avi = Transmute.structure.read(aviBytes.asBytes(), VideoFormat.Avi)

// Round-trip
val raw = Transmute.structure.write(avi)
```

The reader validates the `RIFF....AVI ` signature, then recursively parses `LIST` chunks (`hdrl`,
`movi`) and leaf chunks (`avih`, `strh`, `strf`, `idx1`). See `docs/structures.md`.

## Notes
- Desktop requires the optional `transmute-gstreamer` module with GStreamer installed.
