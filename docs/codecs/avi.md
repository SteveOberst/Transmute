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
| Android | ⚠️ GStreamer plugin | ⚠️ GStreamer plugin |
| Desktop (JVM) | ⚠️ GStreamer plugin | ⚠️ GStreamer plugin |
| iOS | ⚠️ GStreamer plugin | ⚠️ GStreamer plugin |

AVI requires the [GStreamer plugin](../plugins.md) on **all platforms**.

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

AVI uses `CanonicalVideoEncodeOptions` — there are currently no format-specific encoding knobs.

```kotlin
encode {
    options {
        metadataPolicy = MetadataPolicy.PRESERVE   // default: STRIP_ALL
        outputFormat   = OutputFormat.Exact(VideoFormat.Avi)
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

val transmuter = transmute.video.to(VideoFormat.Avi)
val aviBytes = transmuter.transmute(inputBytes)
```

## Inspection

```kotlin
val structure  = Transmute.inspect.structure(aviBytes)   // AviStructure
val inspection = Transmute.inspect.inspect(aviBytes)
// RiffInfoMetadata carries RIFF INFO list chunk fields
```

## Related

- [Codec API](../codec.md)
- [Plugins](../plugins.md)
- [Video transforms](../transforms/README.md)
