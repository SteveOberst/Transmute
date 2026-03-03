# OGG / Vorbis

`AudioFormat.Ogg` — Ogg Vorbis

| Property | Value |
|----------|-------|
| Enum value | `AudioFormat.Ogg` |
| MIME type | `audio/ogg` |
| Extension | `.ogg` |
| Container | Ogg |
| Metadata | `VorbisCommentMetadata` |
| Structure | `OggAudioStructure` |

## Platform support

| Platform | Decode | Encode |
|----------|--------|--------|
| Android | ✅ built-in (MediaCodec) | ✅ built-in |
| Desktop (JVM) | ✅ built-in (decode only) | ⚠️ GStreamer plugin |
| iOS | ⚠️ GStreamer plugin | ⚠️ GStreamer plugin |

On Desktop, decoding OGG is available built-in; encoding requires the [GStreamer plugin](../plugins.md). On iOS, both operations require GStreamer.

## Desktop/iOS plugin setup

```kotlin
val transmute = Transmute {
    plugins {
        install(GStreamerPlugin) {
            domains(MediaDomain.AUDIO)
        }
    }
}
```

## Encode options

OGG uses `CanonicalAudioEncodeOptions` — there are currently no format-specific encoding knobs.

```kotlin
encode {
    options {
        metadataPolicy = MetadataPolicy.PRESERVE   // default: STRIP_ALL
        outputFormat   = OutputFormat.Exact(AudioFormat.Ogg)
    }
}
```

## Basic usage

```kotlin
val transmuter = Transmute.audio.to(AudioFormat.Ogg)
val oggBytes = transmuter.transmute(inputBytes)
```

## Inspection

```kotlin
val structure  = Transmute.inspect.structure(oggBytes)  // OggAudioStructure
val inspection = Transmute.inspect.inspect(oggBytes)
// Vorbis comment tags: artist, album, title, etc.
```

## Related

- [Codec API](../codec.md)
- [Plugins](../plugins.md)
- [Structures](../structures.md)
