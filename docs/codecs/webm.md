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
| Android | ✅ built-in (MediaCodec, VP8/VP9) | ✅ built-in |
| Desktop (JVM) | ⚠️ GStreamer plugin | ⚠️ GStreamer plugin |
| iOS | ⚠️ GStreamer plugin | ⚠️ GStreamer plugin |

On Desktop and iOS, WebM requires the [GStreamer plugin](../plugins.md).

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

WebM uses `CanonicalVideoEncodeOptions` — there are currently no format-specific encoding knobs.

```kotlin
encode {
    options {
        metadataPolicy = MetadataPolicy.PRESERVE   // default: STRIP_ALL
        outputFormat   = OutputFormat.Exact(VideoFormat.Webm)
    }
}
```

## Basic usage

```kotlin
val transmuter = Transmute.video.to(VideoFormat.Webm)
val webmBytes = transmuter.transmute(inputBytes)
```

## Inspection

```kotlin
val structure  = Transmute.inspect.structure(webmBytes)  // WebmStructure
val inspection = Transmute.inspect.inspect(webmBytes)
// MatroskaTagMetadata carries Matroska-style tags
```

## Related

- [Codec API](../codec.md)
- [Plugins](../plugins.md)
- [MKV](mkv.md) — same EBML container family
- [Video transforms](../transforms/README.md)
