# Opus

`AudioFormat.Opus` — Opus Interactive Audio Codec

| Property | Value |
|----------|-------|
| Enum value | `AudioFormat.Opus` |
| MIME type | `audio/opus` |
| Extension | `.opus` |
| Container | Ogg |
| Metadata | `VorbisCommentMetadata` |
| Structure | `OpusStructure` |

## Platform support

| Platform | Decode | Encode |
|----------|--------|--------|
| Android | built-in | hardware dependent |
| Desktop (JVM) | plugin: GStreamer | plugin: GStreamer |
| iOS | plugin: GStreamer | plugin: GStreamer |

On Desktop and iOS, Opus requires the [GStreamer plugin](../plugins.md). On Android, decoding is always available; encoding depends on device hardware support.

## Plugin setup

```kotlin
val transmute = transmute {
    plugins {
        install(GStreamer)
    }
}
```

## Encode parameters

Opus has no format-specific parameter keys today. Use `AudioParamKeys.OutputFormat`
and `AudioParamKeys.EncodeMetadataPolicy` when you need to force Opus output or preserve metadata.

```kotlin
encode {
    params(
        Params.of(
            AudioParamKeys.OutputFormat to OutputFormat.Exact(AudioFormat.Opus),
            AudioParamKeys.EncodeMetadataPolicy to MetadataPolicy.PRESERVE,
        )
    )
}
```

## Basic usage

```kotlin
val transmuter = transmute().audio.to(AudioFormat.Opus)
val opusBytes = transmuter.transmute(inputBytes)
```

## Inspection

```kotlin
val structure  = transmute().inspect.structure(opusBytes)  // OpusStructure
val inspection = transmute().inspect.inspect(opusBytes)
// VorbisCommentMetadata carries ARTIST, ALBUM, TITLE, etc.
```

## Notes

- Opus is a modern, low-latency codec particularly well suited for voice and real-time audio.
- The Ogg container carries Vorbis comment metadata in the same way as `.ogg` (Vorbis) files.

## Related

- [Codec API](../codec.md)
- [Plugins](../plugins.md)
- [OGG](ogg.md)
- [Structures](../structures.md)



