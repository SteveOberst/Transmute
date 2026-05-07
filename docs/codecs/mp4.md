# MP4

`VideoFormat.Mp4` — MPEG-4 Part 14

| Property | Value |
|----------|-------|
| Enum value | `VideoFormat.Mp4` |
| MIME type | `video/mp4` |
| Extension | `.mp4` |
| Container | ISOBMFF |
| Metadata | `ItunesMetadata` |
| Structure | `Mp4Structure` |

## Platform support

| Platform | Decode | Encode |
|----------|--------|--------|
| Android | built-in (MediaCodec) | built-in |
| Desktop (JVM) | plugin: GStreamer | plugin: GStreamer |
| iOS | built-in | built-in |

On Desktop, MP4 requires the [GStreamer plugin](../plugins.md).

## Desktop plugin setup

```kotlin
val transmute = transmute {
    plugins {
        install(GStreamer)
    }
}
```

## Encode parameters

MP4 has no format-specific parameter keys today. Use `VideoParamKeys.OutputFormat`
and `VideoParamKeys.EncodeMetadataPolicy` when you need to force MP4 output or preserve metadata.

```kotlin
encode {
    params(
        Params.of(
            VideoParamKeys.OutputFormat to OutputFormat.Exact(VideoFormat.Mp4),
            VideoParamKeys.EncodeMetadataPolicy to MetadataPolicy.PRESERVE,
        )
    )
}
```

## Basic usage

```kotlin
val transmuter = transmute().video.to(VideoFormat.Mp4) {
    encode {
        params(Params.of(VideoParamKeys.EncodeMetadataPolicy to MetadataPolicy.PRESERVE))
    }
}
val mp4Bytes = transmuter.transmute(inputBytes)
```

## Transforms

```kotlin
val transmuter = transmute().video.to(VideoFormat.Mp4) {
    decode { pipeline { trim(startMs = 0L, endMs = 30_000L) } }
    encode {
        pipeline {
            resize(width = 1280, height = 720)
            frameRate(30f)
        }
    }
}
```

## Inspection and thumbnails

```kotlin
val inspection = transmute().inspect.inspect(mp4Bytes)

// Extract first frame as JPEG
val thumb = transmute().inspect.video.thumbnailFirstFrame(mp4Bytes)
```

> On Desktop, thumbnail extraction requires the GStreamer plugin.

## Related

- [Codec API](../codec.md)
- [Plugins](../plugins.md)
- [MOV](mov.md)
- [Video transforms](../transforms/README.md)



