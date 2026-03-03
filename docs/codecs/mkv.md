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
| Android | ⚠️ GStreamer plugin | ⚠️ GStreamer plugin |
| Desktop (JVM) | ⚠️ GStreamer plugin | ⚠️ GStreamer plugin |
| iOS | ⚠️ GStreamer plugin | ⚠️ GStreamer plugin |

MKV requires the [GStreamer plugin](../plugins.md) on **all platforms**.

## Plugin setup

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

MKV uses `CanonicalVideoEncodeOptions` — there are currently no format-specific encoding knobs.

```kotlin
encode {
    options {
        metadataPolicy = MetadataPolicy.PRESERVE   // default: STRIP_ALL
        outputFormat   = OutputFormat.Exact(VideoFormat.Mkv)
    }
}
```

## Basic usage

```kotlin
val transmute = Transmute {
    plugins {
        install(GStreamerPlugin) { domains(MediaDomain.VIDEO) }
    }
}

val transmuter = transmute.video.to(VideoFormat.Mkv)
val mkvBytes = transmuter.transmute(inputBytes)
```

## Inspection

```kotlin
val structure  = Transmute.inspect.structure(mkvBytes)   // MkvStructure
val inspection = Transmute.inspect.inspect(mkvBytes)
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
