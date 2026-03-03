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
| Android | ✅ built-in (MediaCodec) | ✅ built-in |
| Desktop (JVM) | ⚠️ GStreamer plugin | ⚠️ GStreamer plugin |
| iOS | ✅ built-in | ✅ built-in |

On Desktop, MP4 requires the [GStreamer plugin](../plugins.md).

## Desktop plugin setup

```kotlin
val transmute = Transmute {
    plugins {
        install(GStreamerPlugin) {
            domains(MediaDomain.VIDEO)
        }
    }
}
```

## Encode options

MP4 uses `CanonicalVideoEncodeOptions` — there are currently no format-specific encoding knobs (bitrate, codec profile, etc.).

```kotlin
encode {
    options {
        metadataPolicy = MetadataPolicy.PRESERVE   // default: STRIP_ALL
        outputFormat   = OutputFormat.Exact(VideoFormat.Mp4)
    }
}
```

## Basic usage

```kotlin
val transmuter = Transmute.video.to(VideoFormat.Mp4) {
    encode { options { metadataPolicy = MetadataPolicy.PRESERVE } }
}
val mp4Bytes = transmuter.transmute(inputBytes)
```

## Transforms

```kotlin
val transmuter = Transmute.video.to(VideoFormat.Mp4) {
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
val inspection = Transmute.inspect.inspect(mp4Bytes)

// Extract first frame as JPEG
val thumb = Transmute.inspect.video.thumbnailFirstFrame(mp4Bytes)
```

> On Desktop, thumbnail extraction requires the GStreamer plugin.

## Related

- [Codec API](../codec.md)
- [Plugins](../plugins.md)
- [MOV](mov.md)
- [Video transforms](../transforms/README.md)
