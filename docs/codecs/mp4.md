# MP4 (H.264)

MP4 is the most widely-used video container format, typically containing H.264 (AVC) video and AAC audio. It offers excellent compatibility across all devices and platforms.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅     | MediaCodec |
| Desktop  | ✅     | ✅     | GStreamer (requires `transmute-gstreamer`) |
| iOS      | ✅     | ✅     | AVFoundation / AVAssetWriter |

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

## Notes

- Full encode + decode support on all platforms.
- Android uses hardware-accelerated MediaCodec for H.264.
- Desktop requires the optional `transmute-gstreamer` module with GStreamer installed.
- iOS uses AVFoundation with hardware H.264 encode/decode.
- The safest choice for maximum cross-platform and cross-device compatibility.
- Supports H.264 (AVC) video codec with AAC audio by default.
- Streaming-friendly with proper moov atom placement (faststart).
