# AAC

`AudioFormat.Aac` — Advanced Audio Coding

| Property | Value |
|----------|-------|
| Enum value | `AudioFormat.Aac` |
| MIME type | `audio/aac` |
| Extension | `.aac` |
| Container | ADTS stream / raw AAC |
| Metadata | `ItunesMetadata` |
| Structure | `AacStructure` |

## Platform support

| Platform | Decode | Encode |
|----------|--------|--------|
| Android | ✅ built-in (MediaCodec) | ✅ built-in |
| Desktop (JVM) | ⚠️ GStreamer plugin | ⚠️ GStreamer plugin |
| iOS | ✅ built-in | ✅ built-in |

On Desktop, AAC requires the [GStreamer plugin](../plugins.md).

## Desktop plugin setup

```kotlin
val transmute = Transmute {
    plugins {
        install(GStreamerPlugin) {
            domains(MediaDomain.AUDIO) // or MediaDomain.ALL
        }
    }
}
```

## Encode options

AAC uses `CanonicalAudioEncodeOptions` — there are currently no format-specific encoding knobs.

```kotlin
encode {
    options {
        metadataPolicy = MetadataPolicy.PRESERVE   // default: STRIP_ALL
        outputFormat   = OutputFormat.Exact(AudioFormat.Aac)
    }
}
```

## Basic usage

```kotlin
val transmuter = Transmute.audio.to(AudioFormat.Aac)
val aacBytes = transmuter.transmute(inputBytes)
```

## Inspection

```kotlin
val structure = Transmute.inspect.structure(aacBytes) // AacStructure
val inspection = Transmute.inspect.inspect(aacBytes)
```

## Related

- [Codec API](../codec.md)
- [Plugins](../plugins.md)
- [Structures](../structures.md)
