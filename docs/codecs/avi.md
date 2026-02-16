# AVI

AVI (Audio Video Interleave) is a legacy video container format originally developed by Microsoft. It supports a wide range of video and audio codecs.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ❌     | ❌     | Not supported |
| Desktop  | ✅     | ✅     | FFmpeg (bundled) |
| iOS      | ❌     | ❌     | Not supported |

## Usage

```kotlin
// Convert to AVI (Desktop only)
val aviBytes = Transmute.video(inputBytes) {
    outputFormat(VideoFormat.AVI)
}

// Convert AVI to MP4 (Desktop only)
val mp4Bytes = Transmute.video(aviBytes) {
    outputFormat(VideoFormat.MP4)
}
```

## Notes

- **Desktop only** — no Android or iOS support.
- Requires the bundled FFmpeg on Desktop; no user setup needed.
- AVI is a legacy format; prefer MP4 or MKV for new projects.
- Large file sizes due to limited container-level compression.
- Does not support modern features like streaming, chapters, or subtitles well.
- Useful for interoperability with older software and workflows.
