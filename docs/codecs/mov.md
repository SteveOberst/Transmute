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
| Android | built-in (MediaCodec) | built-in |
| Desktop (JVM) | plugin: GStreamer | plugin: GStreamer |
| iOS | built-in | built-in |

On Desktop, MOV requires the [GStreamer plugin](../plugins.md).

## Desktop plugin setup

```kotlin
val transmute = transmute {
    plugins {
        install(GStreamer)
    }
}
```

## Encode parameters

MOV has no format-specific parameter keys today. Use `VideoParamKeys.OutputFormat`
and `VideoParamKeys.EncodeMetadataPolicy` when you need to force MOV output or preserve metadata.

```kotlin
encode {
    params(
        Params.of(
            VideoParamKeys.OutputFormat to OutputFormat.Exact(VideoFormat.Mov),
            VideoParamKeys.EncodeMetadataPolicy to MetadataPolicy.PRESERVE,
        )
    )
}
```

## Basic usage

```kotlin
val transmuter = transmute().video.to(VideoFormat.Mov)
val movBytes = transmuter.transmute(inputBytes)
```

## Inspection and thumbnails

```kotlin
val structure  = transmute().inspect.structure(movBytes)  // MovStructure
val inspection = transmute().inspect.inspect(movBytes)

// First-frame thumbnail
val thumb = transmute().inspect.video.thumbnailFirstFrame(movBytes)
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



