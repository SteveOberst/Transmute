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
import dev.transmute.Transmute
import dev.transmute.core.VideoFormat
import dev.transmute.video.DefaultVideoEncodeOptions

// Convert video to AVI (Android/Desktop)
suspend fun convertToAvi(inputBytes: ByteArray): ByteArray =
  Transmute.video {
    encodeOptions(DefaultVideoEncodeOptions(outputFormat = VideoFormat.AVI))
  }.transmute(inputBytes).bytes

// Decode AVI (re-encode to MP4)
suspend fun convertToMp4(aviBytes: ByteArray): ByteArray =
  Transmute.video {
    encodeOptions(DefaultVideoEncodeOptions(outputFormat = VideoFormat.MP4))
  }.transmute(aviBytes).bytes
```

## Notes

- Legacy container format; prefer MP4 for modern workflows.
- Desktop encoding relies on the bundled FFmpeg build.
