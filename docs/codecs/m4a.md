# M4A

`AudioFormat.M4a` — MPEG-4 Audio

| Property | Value |
|----------|-------|
| Enum value | `AudioFormat.M4a` |
| MIME type | `audio/mp4` |
| Extension | `.m4a` |
| Container | ISOBMFF (MP4) |
| Metadata | `ItunesMetadata` |
| Structure | `M4aStructure` |

## Platform support

| Platform | Decode | Encode |
|----------|--------|--------|
| Android | built-in (MediaCodec) | built-in |
| Desktop (JVM) | plugin: GStreamer | plugin: GStreamer |
| iOS | built-in | built-in |

On Desktop, M4A requires the [GStreamer plugin](../plugins.md).

## Desktop plugin setup

```kotlin
val transmute = transmute {
    plugins {
        install(GStreamer)
    }
}
```

## Encode parameters

M4A has no format-specific parameter keys today. Use `AudioParamKeys.OutputFormat`
and `AudioParamKeys.EncodeMetadataPolicy` when you need to force M4A output or preserve metadata.

```kotlin
encode {
    params(
        Params.of(
            AudioParamKeys.OutputFormat to OutputFormat.Exact(AudioFormat.M4a),
            AudioParamKeys.EncodeMetadataPolicy to MetadataPolicy.PRESERVE,
        )
    )
}
```

## Basic usage

```kotlin
val transmuter = transmute().audio.to(AudioFormat.M4a)
val m4aBytes = transmuter.transmute(inputBytes)
```

## Inspection

```kotlin
val structure  = transmute().inspect.structure(m4aBytes)  // M4aStructure
val inspection = transmute().inspect.inspect(m4aBytes)
// ItunesMetadata carries iTunes-style atoms: artist, album, cover art, etc.
```

## Notes

- M4A is an audio-only MP4 container, commonly using AAC audio internally.
- iTunes metadata (`©nam`, `©ART`, `©alb`, `covr`, etc.) is preserved when `metadataPolicy = MetadataPolicy.PRESERVE`.

## Related

- [Codec API](../codec.md)
- [Plugins](../plugins.md)
- [AAC](aac.md)
- [MP4](mp4.md)



