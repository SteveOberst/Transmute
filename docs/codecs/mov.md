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
import dev.transmute.core.VideoFormat
import dev.transmute.core.OutputFormat
import dev.transmute.video.CanonicalVideoEncodeOptions

// Convert video to MOV
suspend fun convertToMov(inputBytes: ByteArray): ByteArray =
  Transmute.video {
    encodeOptions(CanonicalVideoEncodeOptions(outputFormat = OutputFormat.Exact(VideoFormat.MOV)))
  }.transmute(inputBytes).bytes

// Decode MOV (re-encode to MP4)
suspend fun convertToMp4(movBytes: ByteArray): ByteArray =
  Transmute.video {
    encodeOptions(CanonicalVideoEncodeOptions(outputFormat = OutputFormat.Exact(VideoFormat.MP4)))
  }.transmute(movBytes).bytes
```

## Notes

- Great compatibility within Apple ecosystems.
- Desktop encoding relies on the bundled FFmpeg build.
