# WebM

WebM is an open, royalty-free video container format developed by Google. It uses VP8 or VP9 video codecs with Vorbis or Opus audio.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ❌     | MediaCodec (decode only) |
| Desktop  | ✅     | ✅     | FFmpeg (bundled) |
| iOS      | ❌     | ❌     | Not supported |

## Usage

```kotlin
// Encode to WebM (Desktop only)
val webmBytes = Transmute.video(inputBytes) {
    outputFormat(VideoFormat.WEBM)
}

// Decode WebM to MP4 (Android/Desktop)
val mp4Bytes = Transmute.video(webmBytes) {
    outputFormat(VideoFormat.MP4)
}
```

## Notes

- **iOS does not support WebM** - neither decode nor encode.
- Android can **decode** WebM (VP8/VP9) but cannot encode to it.
- Desktop uses the bundled FFmpeg for full VP8/VP9 encode and decode.
- Royalty-free - no patent licensing concerns.
- Commonly used for web video (HTML5 `<video>` element).
- Supports VP8, VP9, and AV1 video codecs; Vorbis and Opus audio.
- Consider MP4/H.264 if cross-platform compatibility is required.
