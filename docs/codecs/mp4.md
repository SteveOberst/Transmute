# MP4 (H.264)

MP4 is the most widely-used video container format, typically containing H.264 (AVC) video and AAC audio. It offers excellent compatibility across all devices and platforms.

## Platform Support

| Platform | Decode | Encode | Engine                                     |
|----------|--------|--------|--------------------------------------------|
| Android  | ✅      | ✅      | MediaCodec                                 |
| Desktop  | ✅      | ✅      | GStreamer (requires `transmute-gstreamer`) |
| iOS      | ✅      | ✅      | AVFoundation / AVAssetWriter               |

## Usage

```kotlin
// Convert video to MP4
suspend fun convertToMp4(inputBytes: ByteArray): ByteArray =
  Transmute.video {
    encode { options { outputFormat = OutputFormat.Exact(VideoFormat.Mp4) } }
  }.transmute(inputBytes.asBytes()).bytes.data

// Decode MP4 to another format (re-encode to WebM)
suspend fun convertToWebm(mp4Bytes: ByteArray): ByteArray =
  Transmute.video {
    encode { options { outputFormat = OutputFormat.Exact(VideoFormat.Webm) } }
  }.transmute(mp4Bytes.asBytes()).bytes.data
```

## Structure Reading

MP4 files can be parsed into an `Mp4` structure that mirrors the ISO BMFF box layout:

```kotlin
val mp4: Mp4 = Transmute.structure.read(mp4Bytes.asBytes(), VideoFormat.Mp4)

// Round-trip
val raw = Transmute.structure.write(mp4)
```

The reader walks `ftyp`, `moov`, `mdat`, and all nested boxes. Common inspection targets include
`moov.trak[0].mdia.hdlr` (handler type) and `moov.mvhd` (movie header / duration). See `docs/structures.md`.

## Notes
- Android uses hardware-accelerated MediaCodec for H.264.
- Desktop requires the optional `transmute-gstreamer` module with GStreamer installed.
- iOS uses AVFoundation with hardware H.264 encode/decode.
- The safest choice for maximum cross-platform and cross-device compatibility.
- Supports H.264 (AVC) video codec with AAC audio by default.
- Streaming-friendly with proper moov atom placement (faststart).
