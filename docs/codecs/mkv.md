# MKV

`VideoFormat.Mkv` — Matroska Video

| Property | Value |
|----------|-------|
| Enum value | `VideoFormat.Mkv` |
| MIME type | `video/x-matroska` |
| Extension | `.mkv` |
| Container | EBML |
| Metadata | `MatroskaTagMetadata` |
| Structure | `MkvStructure` |

## Platform support

| Platform | Decode | Encode |
|----------|--------|--------|
| Android | plugin: GStreamer | plugin: GStreamer |
| Desktop (JVM) | plugin: GStreamer | plugin: GStreamer |
| iOS | plugin: GStreamer | plugin: GStreamer |

MKV requires the [GStreamer plugin](../plugins.md) on **all platforms**.

## Plugin setup

```kotlin
val transmute = transmute {
    plugins {
        install(GStreamer)
    }
}
```

## Encode parameters

MKV has no format-specific parameter keys today. Use `VideoParamKeys.OutputFormat`
and `VideoParamKeys.EncodeMetadataPolicy` when you need to force MKV output or preserve metadata.

```kotlin
encode {
    params(
        Params.of(
            VideoParamKeys.OutputFormat to OutputFormat.Exact(VideoFormat.Mkv),
            VideoParamKeys.EncodeMetadataPolicy to MetadataPolicy.PRESERVE,
        )
    )
}
```

## Basic usage

```kotlin
val transmute = transmute {
    plugins {
        install(GStreamer)
    }
}

val transmuter = transmute.video.to(VideoFormat.Mkv)
val mkvBytes = transmuter.transmute(inputBytes)
```

## Inspection

```kotlin
val structure  = transmute().inspect.structure(mkvBytes)   // MkvStructure
val inspection = transmute().inspect.inspect(mkvBytes)
// MatroskaTagMetadata carries Matroska tags
```

## Notes

- MKV is the same EBML container as WebM but allows any video/audio codec (not restricted to VP8/VP9/Opus/Vorbis).
- Matroska is commonly used for high-quality encodes with subtitle and chapter support.

## Related

- [Codec API](../codec.md)
- [Plugins](../plugins.md)
- [WebM](webm.md) — sibling EBML container
- [Video transforms](../transforms/README.md)



