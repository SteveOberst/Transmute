# MOV (QuickTime)

MOV is Apple's QuickTime container format. It commonly contains H.264 video and AAC audio and is widely used in Apple workflows.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅     | MediaCodec |
| Desktop  | ✅     | ✅     | FFmpeg (bundled) |
| iOS      | ✅     | ✅     | AVFoundation / AVAssetWriter |

## Usage

```kotlin
import dev.transmute.Transmute
import dev.transmute.core.OutputFormat
import dev.transmute.core.asBytes
import dev.transmute.video.CanonicalVideoEncodeOptions
import dev.transmute.video.VideoFormat

// Convert video to MOV
suspend fun convertToMov(inputBytes: ByteArray): ByteArray =
  Transmute.video {
    encode { options(CanonicalVideoEncodeOptions(outputFormat = OutputFormat.Exact(VideoFormat.Mov))) }
  }.transmute(inputBytes.asBytes()).bytes.data

// Decode MOV (re-encode to MP4)
suspend fun convertToMp4(movBytes: ByteArray): ByteArray =
  Transmute.video {
    encode { options(CanonicalVideoEncodeOptions(outputFormat = OutputFormat.Exact(VideoFormat.Mp4))) }
  }.transmute(movBytes.asBytes()).bytes.data
```

## Notes

- Great compatibility within Apple ecosystems.
- Desktop encoding relies on the bundled FFmpeg build.
