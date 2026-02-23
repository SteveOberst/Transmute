# AVI

AVI is a legacy video container format. It is still encountered frequently in archives and older workflows.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅     | MediaCodec (decode) / FFmpeg (encode) |
| Desktop  | ✅     | ✅     | FFmpeg (bundled) |
| iOS      | ✅     | ❌     | AVFoundation (decode only) |

## Usage

```kotlin
// Convert video to AVI (Android/Desktop)
suspend fun convertToAvi(inputBytes: ByteArray): ByteArray =
  Transmute.video {
    encode { options(CanonicalVideoEncodeOptions(outputFormat = OutputFormat.Exact(VideoFormat.Avi))) }
  }.transmute(inputBytes.asBytes()).bytes.data

// Decode AVI (re-encode to MP4)
suspend fun convertToMp4(aviBytes: ByteArray): ByteArray =
  Transmute.video {
    encode { options(CanonicalVideoEncodeOptions(outputFormat = OutputFormat.Exact(VideoFormat.Mp4))) }
  }.transmute(aviBytes.asBytes()).bytes.data
```

## Notes

- Legacy container format; prefer MP4 for modern workflows.
- Desktop encoding relies on the bundled FFmpeg build.
