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
| Android | built-in | built-in |
| Desktop (JVM) | built-in (pure Kotlin) | built-in (pure Kotlin) |
| iOS | built-in | built-in |

WAV is implemented as a pure-Kotlin codec and works on **all platforms** without any plugins.

## Encode options

WAV has no format-specific parameter keys today. Use `AudioParamKeys.OutputFormat`
and `AudioParamKeys.EncodeMetadataPolicy` when you need to force WAV output or preserve metadata.

```kotlin
encode {
    params(
        Params.of(
            AudioParamKeys.EncodeMetadataPolicy to MetadataPolicy.PRESERVE,
            AudioParamKeys.OutputFormat to OutputFormat.Exact(AudioFormat.Wav),
        )
    )
}
```

## Basic usage

```kotlin
// Convert any audio to WAV (uncompressed)
val transmuter = transmute().audio.to(AudioFormat.Wav)
val wavBytes = transmuter.transmute(inputBytes)

// Preserve RIFF INFO chunk metadata
val transmuter = transmute().audio.to(AudioFormat.Wav) {
    encode {
        params(Params.of(AudioParamKeys.EncodeMetadataPolicy to MetadataPolicy.PRESERVE))
    }
}
```

## Inspection

```kotlin
val structure = transmute().inspect.structure(wavBytes) // WavStructure — includes fmt chunk info
val inspection = transmute().inspect.inspect(wavBytes)
val riffInfo = inspection.metadata.filterIsInstance<RiffInfoMetadata>().firstOrNull()
```

## Notes

- WAV is an uncompressed PCM container. Converting to WAV always produces large files.
- The RIFF INFO chunk carries text metadata fields such as artist, title, comment, etc.

## Related

- [Codec API](../codec.md)
- [Inspection](../inspect.md)
- [Structures](../structures.md)



