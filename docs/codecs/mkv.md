# MKV (Matroska)

MKV (Matroska) is a flexible open container format commonly used for high-quality video files and archives.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅     | MediaCodec (decode) / FFmpeg (encode) |
| Desktop  | ✅     | ✅     | FFmpeg (bundled) |
| iOS      | ✅     | ❌     | AVFoundation (decode only) |

## Usage

```kotlin
import dev.transmute.Transmute
import dev.transmute.core.VideoFormat
import dev.transmute.core.OutputFormat
import dev.transmute.video.CanonicalVideoEncodeOptions

// Convert video to MKV (Android/Desktop)
suspend fun convertToMkv(inputBytes: ByteArray): ByteArray =
  Transmute.video {
    encodeOptions(CanonicalVideoEncodeOptions(outputFormat = OutputFormat.Exact(VideoFormat.MKV)))
  }.transmute(inputBytes).bytes

// Decode MKV (re-encode to MP4)
suspend fun convertToMp4(mkvBytes: ByteArray): ByteArray =
  Transmute.video {
    encodeOptions(CanonicalVideoEncodeOptions(outputFormat = OutputFormat.Exact(VideoFormat.MP4)))
  }.transmute(mkvBytes).bytes
```

## Notes

- Very flexible container; common in archival and enthusiast workflows.
- Desktop encoding relies on the bundled FFmpeg build.
