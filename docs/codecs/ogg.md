# OGG / Vorbis

`AudioFormat.Ogg` — Ogg Vorbis

| Property | Value |
|----------|-------|
| Enum value | `AudioFormat.Ogg` |
| MIME type | `audio/ogg` |
| Extension | `.ogg` |
| Container | Ogg |
| Metadata | `VorbisCommentMetadata` |
| Structure | `OggAudioStructure` |

## Platform support

| Platform | Decode | Encode |
|----------|--------|--------|
| Android | built-in (MediaCodec) | built-in |
| Desktop (JVM) | built-in (decode only) | plugin: GStreamer |
| iOS | plugin: GStreamer | plugin: GStreamer |

On Desktop, decoding OGG is available built-in; encoding requires the [GStreamer plugin](../plugins.md). On iOS, both operations require GStreamer.

## Desktop/iOS plugin setup

```kotlin
val transmute = transmute {
    plugins {
        install(GStreamer)
    }
}
```

## Encode parameters

OGG has no format-specific parameter keys today. Use `AudioParamKeys.OutputFormat`
and `AudioParamKeys.EncodeMetadataPolicy` when you need to force OGG output or preserve metadata.

```kotlin
encode {
    params(
        Params.of(
            AudioParamKeys.OutputFormat to OutputFormat.Exact(AudioFormat.Ogg),
            AudioParamKeys.EncodeMetadataPolicy to MetadataPolicy.PRESERVE,
        )
    )
}
```

## Basic usage

```kotlin
val transmuter = transmute().audio.to(AudioFormat.Ogg)
val oggBytes = transmuter.transmute(inputBytes)
```

## Inspection

```kotlin
val structure  = transmute().inspect.structure(oggBytes)  // OggAudioStructure
val inspection = transmute().inspect.inspect(oggBytes)
// Vorbis comment tags: artist, album, title, etc.
```

## Related

- [Codec API](../codec.md)
- [Plugins](../plugins.md)
- [Structures](../structures.md)



