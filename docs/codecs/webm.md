# WebM

WebM is an open video container format often containing VP8/VP9 video and Opus/Vorbis audio. It's widely used on the web and in open ecosystems.

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
import dev.transmute.video.DefaultVideoEncodeOptions

// Convert video to WebM (Android/Desktop)
suspend fun convertToWebm(inputBytes: ByteArray): ByteArray =
  Transmute.video {
    encodeOptions(DefaultVideoEncodeOptions(outputFormat = VideoFormat.WEBM))
  }.transmute(inputBytes).bytes

// Decode WebM (re-encode to MP4)
suspend fun convertToMp4(webmBytes: ByteArray): ByteArray =
  Transmute.video {
    encodeOptions(DefaultVideoEncodeOptions(outputFormat = VideoFormat.MP4))
  }.transmute(webmBytes).bytes
```

## Notes

- Open and royalty-free container format.
- iOS can decode WebM (via platform support) but cannot encode to it.
- Desktop encoding relies on the bundled FFmpeg build.
