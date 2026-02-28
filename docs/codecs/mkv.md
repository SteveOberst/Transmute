# MKV (Matroska)

MKV (Matroska) is a flexible open container format commonly used for high-quality video files and archives.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅     | MediaCodec |
| Desktop  | ✅     | ✅     | GStreamer (requires `transmute-gstreamer`) |
| iOS      | ✅     | ❌     | AVFoundation (decode only) |

## Usage

```kotlin
// Convert video to MKV (Android/Desktop)
suspend fun convertToMkv(inputBytes: ByteArray): ByteArray =
  Transmute.video {
    encode { options { outputFormat = OutputFormat.Exact(VideoFormat.Mkv) } }
  }.transmute(inputBytes.asBytes()).bytes.data

// Decode MKV (re-encode to MP4)
suspend fun convertToMp4(mkvBytes: ByteArray): ByteArray =
  Transmute.video {
    encode { options { outputFormat = OutputFormat.Exact(VideoFormat.Mp4) } }
  }.transmute(mkvBytes.asBytes()).bytes.data
```

## Structure Reading

MKV files can be parsed into a `Mkv` structure that mirrors the EBML element hierarchy:

```kotlin
val mkv: Mkv = Transmute.structure.read(mkvBytes.asBytes(), VideoFormat.Mkv)

// Round-trip
val raw = Transmute.structure.write(mkv)
```

MKV shares the EBML format with WebM. The reader validates the EBML header and walks Segment,
SeekHead, Info, Tracks, Chapters, and Cluster elements. See `docs/structures.md`.

## Notes
- Desktop requires the optional `transmute-gstreamer` module with GStreamer installed.
