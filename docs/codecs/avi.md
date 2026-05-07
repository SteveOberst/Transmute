# AVI

`VideoFormat.Avi` — Audio Video Interleave

| Property | Value |
|----------|-------|
| Enum value | `VideoFormat.Avi` |
| MIME type | `video/x-msvideo` |
| Extension | `.avi` |
| Container | RIFF |
| Metadata | `RiffInfoMetadata` |
| Structure | `AviStructure` |

## Platform support

| Platform | Decode | Encode |
|----------|--------|--------|
| Android | plugin: GStreamer | plugin: GStreamer |
| Desktop (JVM) | plugin: GStreamer | plugin: GStreamer |
| iOS | plugin: GStreamer | plugin: GStreamer |

AVI requires the [GStreamer plugin](../plugins.md) on **all platforms**.

## Plugin setup

```kotlin
val transmute = transmute {
    plugins {
        install(GStreamer)
    }
}
```

## Encode parameters

AVI has no format-specific parameter keys today. Use `VideoParamKeys.OutputFormat`
and `VideoParamKeys.EncodeMetadataPolicy` when you need to force AVI output or preserve metadata.

```kotlin
encode {
    params(
        Params.of(
            VideoParamKeys.OutputFormat to OutputFormat.Exact(VideoFormat.Avi),
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

val transmuter = transmute.video.to(VideoFormat.Avi)
val aviBytes = transmuter.transmute(inputBytes)
```

## Inspection

```kotlin
val structure  = transmute().inspect.structure(aviBytes)   // AviStructure
val inspection = transmute().inspect.inspect(aviBytes)
// RiffInfoMetadata carries RIFF INFO list chunk fields
```

## Related

- [Codec API](../codec.md)
- [Plugins](../plugins.md)
- [Video transforms](../transforms/README.md)



