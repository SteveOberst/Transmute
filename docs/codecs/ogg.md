# OGG Vorbis

OGG Vorbis is an open-source, royalty-free lossy audio format. It generally offers better quality than MP3 at equivalent bitrates.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ❌     | MediaCodec (decode only) |
| Desktop  | ✅     | ✅     | jorbis (decode) / FFmpeg (encode) |
| iOS      | ❌     | ❌     | Not supported |

## Usage

```kotlin
// Encode to OGG Vorbis (Desktop only)
val oggBytes = Transmute.audio(inputBytes) {
    outputFormat(AudioFormat.OGG)
}

// Decode OGG to WAV (Android/Desktop)
val wavBytes = Transmute.audio(oggBytes) {
    outputFormat(AudioFormat.WAV)
}
```

## Notes

- **iOS does not support OGG Vorbis** - neither decode nor encode.
- Android can **decode** OGG but cannot encode to it.
- Desktop decoding uses jorbis (pure-Java); encoding uses the bundled FFmpeg.
- Royalty-free alternative to MP3 and AAC.
- Commonly used in games, open-source projects, and web audio.
- Consider AAC or OPUS as cross-platform alternatives if iOS support is needed.
