# AAC

`AudioFormat.Aac` — Advanced Audio Coding

| Property | Value |
|----------|-------|
| Enum value | `AudioFormat.Aac` |
| MIME type | `audio/aac` |
| Extension | `.aac` |
| Container | ADTS stream / raw AAC |
| Metadata | `ItunesMetadata` |
| Structure | `AacStructure` |

## Platform support

| Platform | Decode | Encode |
|----------|--------|--------|
| Android | built-in (MediaCodec) | built-in |
| Desktop (JVM) | plugin: GStreamer | plugin: GStreamer |
| iOS | built-in | built-in |

On Desktop, AAC requires the [GStreamer plugin](../plugins.md).

## Desktop plugin setup

```kotlin
val transmute = transmute {
    plugins {
        install(GStreamer)
    }
}
```

## Encode parameters

AAC has no format-specific parameter keys today. Use `AudioParamKeys.OutputFormat`
and `AudioParamKeys.EncodeMetadataPolicy` when you need to force AAC output or preserve metadata.

```kotlin
encode {
    params(
        Params.of(
            AudioParamKeys.OutputFormat to OutputFormat.Exact(AudioFormat.Aac),
            AudioParamKeys.EncodeMetadataPolicy to MetadataPolicy.PRESERVE,
        )
    )
}
```

## Basic usage

```kotlin
val transmuter = transmute().audio.to(AudioFormat.Aac)
val aacBytes = transmuter.transmute(inputBytes)
```

## Inspection

```kotlin
val structure = transmute().inspect.structure(aacBytes) // AacStructure
val inspection = transmute().inspect.inspect(aacBytes)
```

## Related

- [Codec API](../codec.md)
- [Plugins](../plugins.md)
- [Structures](../structures.md)



