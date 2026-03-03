# WAV

`AudioFormat.Wav` — Waveform Audio File Format

| Property | Value |
|----------|-------|
| Enum value | `AudioFormat.Wav` |
| MIME type | `audio/wav` |
| Extension | `.wav` |
| Container | RIFF |
| Metadata | `RiffInfoMetadata` |
| Structure | `WavStructure` |

## Platform support

| Platform | Decode | Encode |
|----------|--------|--------|
| Android | ✅ built-in | ✅ built-in |
| Desktop (JVM) | ✅ built-in | ✅ built-in |
| iOS | ✅ built-in | ✅ built-in |

WAV is implemented as a pure-Kotlin codec and works on **all platforms** without any plugins.

## Encode options

WAV uses `CanonicalAudioEncodeOptions` — there are currently no format-specific encoding knobs.

```kotlin
encode {
    options {
        metadataPolicy = MetadataPolicy.PRESERVE   // default: STRIP_ALL
        outputFormat   = OutputFormat.Exact(AudioFormat.Wav)
    }
}
```

## Basic usage

```kotlin
// Convert any audio to WAV (uncompressed)
val transmuter = Transmute.audio.to(AudioFormat.Wav)
val wavBytes = transmuter.transmute(inputBytes)

// Preserve RIFF INFO chunk metadata
val transmuter = Transmute.audio.to(AudioFormat.Wav) {
    encode { options { metadataPolicy = MetadataPolicy.PRESERVE } }
}
```

## Inspection

```kotlin
val structure = Transmute.inspect.structure(wavBytes) // WavStructure — includes fmt chunk info
val inspection = Transmute.inspect.inspect(wavBytes)
val riffInfo = inspection.metadata  // RiffInfoMetadata
```

## Notes

- WAV is an uncompressed PCM container. Converting to WAV always produces large files.
- The RIFF INFO chunk carries text metadata fields such as artist, title, comment, etc.

## Related

- [Codec API](../codec.md)
- [Inspection](../inspect.md)
- [Structures](../structures.md)
