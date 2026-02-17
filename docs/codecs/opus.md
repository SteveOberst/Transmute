# OPUS

OPUS is a versatile, open-source, royalty-free lossy audio codec. It excels at both speech and music, offering superior quality at low bitrates compared to MP3, AAC, and Vorbis.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅*    | MediaCodec (*encode requires API 29+) |
| Desktop  | ✅     | ✅     | FFmpeg (bundled) |
| iOS      | ❌     | ❌     | Not supported |

## Usage

```kotlin
// Encode to OPUS (Android API 29+ / Desktop)
val opusBytes = Transmute.audio(inputBytes) {
    outputFormat(AudioFormat.OPUS)
}

// Decode OPUS to WAV (Android / Desktop)
val wavBytes = Transmute.audio(opusBytes) {
    outputFormat(AudioFormat.WAV)
}
```

## Notes

- **iOS does not support OPUS** - neither decode nor encode.
- Android decoding works on any supported API level; **encoding requires API 29+**.
- Desktop uses the bundled FFmpeg for full encode + decode.
- Best-in-class quality at low bitrates (6–510 kbps).
- Royalty-free and standardized as RFC 6716.
- Commonly used in WebRTC, VoIP, and streaming applications.
- OPUS audio is typically wrapped in an OGG or WebM container.
