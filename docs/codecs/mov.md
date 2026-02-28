# MOV (QuickTime)

MOV is Apple's QuickTime container format. It commonly contains H.264 video and AAC audio and is widely used in Apple workflows.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅     | MediaCodec |
| Desktop  | ✅     | ✅     | GStreamer (requires `transmute-gstreamer`) |
| iOS      | ✅     | ✅     | AVFoundation / AVAssetWriter |

## Usage

```kotlin
// Convert video to MOV
suspend fun convertToMov(inputBytes: ByteArray): ByteArray =
  Transmute.video {
    encode { options { outputFormat = OutputFormat.Exact(VideoFormat.Mov) } }
  }.transmute(inputBytes.asBytes()).bytes.data

// Decode MOV (re-encode to MP4)
suspend fun convertToMp4(movBytes: ByteArray): ByteArray =
  Transmute.video {
    encode { options { outputFormat = OutputFormat.Exact(VideoFormat.Mp4) } }
  }.transmute(movBytes.asBytes()).bytes.data
```

## Structure Reading

MOV files can be parsed into a `Mov` structure that mirrors the ISO BMFF / QuickTime box layout:

```kotlin
val mov: Mov = Transmute.structure.read(movBytes.asBytes(), VideoFormat.Mov)

// Round-trip
val raw = Transmute.structure.write(mov)
```

The reader handles both `ftyp`-leading files (modern MOV) and bare `moov`-first QuickTime files.
See `docs/structures.md`.

## Notes
- Desktop requires the optional `transmute-gstreamer` module with GStreamer installed.
