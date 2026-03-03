# MOV

`VideoFormat.Mov` — QuickTime Movie

| Property | Value |
|----------|-------|
| Enum value | `VideoFormat.Mov` |
| MIME type | `video/quicktime` |
| Extension | `.mov` |
| Container | ISOBMFF (QuickTime variant) |
| Metadata | `ItunesMetadata` |
| Structure | `MovStructure` |

## Platform support

| Platform | Decode | Encode |
|----------|--------|--------|
| Android | ✅ built-in (MediaCodec) | ✅ built-in |
| Desktop (JVM) | ⚠️ GStreamer plugin | ⚠️ GStreamer plugin |
| iOS | ✅ built-in | ✅ built-in |

On Desktop, MOV requires the [GStreamer plugin](../plugins.md).

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

MOV uses `CanonicalVideoEncodeOptions` — there are currently no format-specific encoding knobs.

```kotlin
encode {
    options {
        metadataPolicy = MetadataPolicy.PRESERVE   // default: STRIP_ALL
        outputFormat   = OutputFormat.Exact(VideoFormat.Mov)
    }
}
```

## Basic usage

```kotlin
val transmuter = Transmute.video.to(VideoFormat.Mov)
val movBytes = transmuter.transmute(inputBytes)
```

## Inspection and thumbnails

```kotlin
val structure  = Transmute.inspect.structure(movBytes)  // MovStructure
val inspection = Transmute.inspect.inspect(movBytes)

// First-frame thumbnail
val thumb = Transmute.inspect.video.thumbnailFirstFrame(movBytes)
```

> On Desktop, thumbnail extraction requires the GStreamer plugin.

## Notes

- MOV and MP4 share the same ISOBMFF container format; many files are interchangeable at the container level.
- Apple devices write camera recordings as MOV files.

## Related

- [Codec API](../codec.md)
- [Plugins](../plugins.md)
- [MP4](mp4.md)
- [Video transforms](../transforms/README.md)
