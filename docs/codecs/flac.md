# FLAC

`AudioFormat.Flac` — Free Lossless Audio Codec

| Property | Value |
|----------|-------|
| Enum value | `AudioFormat.Flac` |
| MIME type | `audio/flac` |
| Extension | `.flac` |
| Container | FLAC native |
| Metadata | `VorbisCommentMetadata` |
| Structure | `FlacStructure` |

## Platform support

| Platform | Decode | Encode |
|----------|--------|--------|
| Android | built-in (MediaCodec) | built-in |
| Desktop (JVM) | built-in (decode only) | plugin: GStreamer |
| iOS | built-in | built-in |

On Desktop, FLAC decoding is available built-in; encoding requires the [GStreamer plugin](../plugins.md).

## Desktop plugin setup (encode only)

```kotlin
val transmute = transmute {
    plugins {
        install(GStreamer)
    }
}
```

## Encode parameters

FLAC has no format-specific parameter keys today. Use `AudioParamKeys.OutputFormat`
and `AudioParamKeys.EncodeMetadataPolicy` when you need to force FLAC output or preserve metadata.

```kotlin
encode {
    params(
        Params.of(
            AudioParamKeys.OutputFormat to OutputFormat.Exact(AudioFormat.Flac),
            AudioParamKeys.EncodeMetadataPolicy to MetadataPolicy.PRESERVE,
        )
    )
}
```

## Basic usage

```kotlin
// Decode FLAC to PCM-backed intermediate, re-encode as FLAC
val transmuter = transmute().audio.to(AudioFormat.Flac) {
    encode {
        params(Params.of(AudioParamKeys.EncodeMetadataPolicy to MetadataPolicy.PRESERVE))
    }
}
val flacBytes = transmuter.transmute(inputBytes)
```

## Inspection

```kotlin
val structure  = transmute().inspect.structure(flacBytes)  // FlacStructure
val inspection = transmute().inspect.inspect(flacBytes)
// VorbisCommentMetadata carries ARTIST, ALBUM, TITLE, etc.
```

## Notes

- FLAC is lossless; the re-encoded output is bit-for-bit equivalent to the source audio, not necessarily to the original FLAC file (encoder parameters may differ).

## Related

- [Codec API](../codec.md)
- [Plugins](../plugins.md)
- [Structures](../structures.md)



