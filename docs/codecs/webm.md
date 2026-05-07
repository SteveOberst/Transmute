# WebM

`VideoFormat.Webm` — WebM

| Property | Value |
|----------|-------|
| Enum value | `VideoFormat.Webm` |
| MIME type | `video/webm` |
| Extension | `.webm` |
| Container | EBML (Matroska variant) |
| Metadata | `MatroskaTagMetadata` |
| Structure | `WebmStructure` |

## Platform support

| Platform | Decode | Encode |
|----------|--------|--------|
| Android | built-in (MediaCodec, VP8/VP9) | built-in |
| Desktop (JVM) | plugin: GStreamer | plugin: GStreamer |
| iOS | plugin: GStreamer | plugin: GStreamer |

On Desktop and iOS, WebM requires the [GStreamer plugin](../plugins.md).

## Plugin setup

```kotlin
val transmute = transmute {
    plugins {
        install(GStreamer)
    }
}
```

## Encode parameters

WebM has no format-specific parameter keys today. Use `VideoParamKeys.OutputFormat`
and `VideoParamKeys.EncodeMetadataPolicy` when you need to force WebM output or preserve metadata.

```kotlin
encode {
    params(
        Params.of(
            VideoParamKeys.OutputFormat to OutputFormat.Exact(VideoFormat.Webm),
            VideoParamKeys.EncodeMetadataPolicy to MetadataPolicy.PRESERVE,
        )
    )
}
```

## Basic usage

```kotlin
val transmuter = transmute().video.to(VideoFormat.Webm)
val webmBytes = transmuter.transmute(inputBytes)
```

## Inspection

```kotlin
val structure  = transmute().inspect.structure(webmBytes)  // WebmStructure
val inspection = transmute().inspect.inspect(webmBytes)
// MatroskaTagMetadata carries Matroska-style tags
```

## Related

- [Codec API](../codec.md)
- [Plugins](../plugins.md)
- [MKV](mkv.md) — same EBML container family
- [Video transforms](../transforms/README.md)



