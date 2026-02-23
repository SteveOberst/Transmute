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

## Notes

- Open and royalty-free container format.
- iOS can decode WebM (via platform support) but cannot encode to it.
- Desktop encoding relies on the bundled FFmpeg build.
