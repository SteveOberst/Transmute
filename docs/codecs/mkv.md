# MKV (Matroska)

MKV (Matroska Video) is an open, flexible video container format that can hold virtually any combination of video, audio, subtitle, and metadata tracks.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ❌     | ❌     | Not supported |
| Desktop  | ✅     | ✅     | FFmpeg (bundled) |
| iOS      | ❌     | ❌     | Not supported |

## Usage

```kotlin
// Convert to MKV (Desktop only)
val mkvBytes = Transmute.video(inputBytes) {
    outputFormat(VideoFormat.MKV)
}

// Convert MKV to MP4 (Desktop only)
val mp4Bytes = Transmute.video(mkvBytes) {
    outputFormat(VideoFormat.MP4)
}
```

## Notes

- **Desktop only** - no Android or iOS support.
- Requires the bundled FFmpeg on Desktop; no user setup needed.
- Extremely flexible container - supports H.264, H.265, VP9, AV1, and many more codecs.
- Open-source and royalty-free (Matroska specification).
- Supports multiple audio tracks, subtitles, chapters, and rich metadata.
- Popular for media archival and high-quality video distribution.
- Consider MP4 if cross-platform playback is required.
