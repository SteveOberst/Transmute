# WebM

WebM is an open video container format often containing VP8/VP9 video and Opus/Vorbis audio. It's widely used on the web and in open ecosystems.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅     | MediaCodec |
| Desktop  | ✅     | ✅     | GStreamer (requires `transmute-gstreamer`) |
| iOS      | ✅     | ❌     | AVFoundation (decode only) |

## Usage

```kotlin
// Convert video to WebM (Android/Desktop)
suspend fun convertToWebm(inputBytes: ByteArray): ByteArray =
  Transmute.video {
    encode { options { outputFormat = OutputFormat.Exact(VideoFormat.Webm) } }
  }.transmute(inputBytes.asBytes()).bytes.data

// Decode WebM (re-encode to MP4)
suspend fun convertToMp4(webmBytes: ByteArray): ByteArray =
  Transmute.video {
    encode { options { outputFormat = OutputFormat.Exact(VideoFormat.Mp4) } }
  }.transmute(webmBytes.asBytes()).bytes.data
```

## Structure Reading

WebM files can be parsed into a `Webm` structure that mirrors the EBML element hierarchy:

```kotlin
val webm: Webm = Transmute.structure.read(webmBytes.asBytes(), VideoFormat.Webm)

// Round-trip
val raw = Transmute.structure.write(webm)
```

The reader validates the EBML header (magic `0x1A 0x45 0xDF 0xA3`), then walks Segment, SeekHead,
Info, Tracks, and Cluster elements. See `docs/structures.md`.

## Notes
- iOS can decode WebM (via platform support) but cannot encode to it.
- Desktop requires the optional `transmute-gstreamer` module with GStreamer installed.
