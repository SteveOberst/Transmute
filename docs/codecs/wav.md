# WAV

WAV (Waveform Audio File Format) is an uncompressed audio format that preserves full audio fidelity. Transmute includes a **pure-Kotlin WAV codec** that works identically across all platforms.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ✅     | Pure-Kotlin |
| Desktop  | ✅     | ✅     | Pure-Kotlin |
| iOS      | ✅     | ✅     | Pure-Kotlin |

## Usage

```kotlin
// Convert any audio to WAV
val wavBytes = Transmute.audio(inputBytes) {
    outputFormat(AudioFormat.WAV)
}

// Convert WAV to another format
val aacBytes = Transmute.audio(wavBytes) {
    outputFormat(AudioFormat.AAC)
}
```

## Notes

- The WAV codec is **pure Kotlin** - identical behavior across all platforms with no native dependencies.
- WAV files are uncompressed and can be very large (≈10 MB/min for 16-bit stereo at 44.1 kHz).
- Supports standard PCM encoding (16-bit, 44.1 kHz stereo by default).
- Commonly used as an intermediate format for lossless audio processing.
- No quality parameter - WAV is always uncompressed/lossless.
