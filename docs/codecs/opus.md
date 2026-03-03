# Opus

`AudioFormat.Opus` — Opus Interactive Audio Codec

| Property | Value |
|----------|-------|
| Enum value | `AudioFormat.Opus` |
| MIME type | `audio/opus` |
| Extension | `.opus` |
| Container | Ogg |
| Metadata | `VorbisCommentMetadata` |
| Structure | `OpusStructure` |

## Platform support

| Platform | Decode | Encode |
|----------|--------|--------|
| Android | ✅ built-in (decode); ⚠️ encode requires hardware | ✅ (hardware dependent) |
| Desktop (JVM) | ⚠️ GStreamer plugin | ⚠️ GStreamer plugin |
| iOS | ⚠️ GStreamer plugin | ⚠️ GStreamer plugin |

On Desktop and iOS, Opus requires the [GStreamer plugin](../plugins.md). On Android, decoding is always available; encoding depends on device hardware support.

## Plugin setup

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

Opus uses `CanonicalAudioEncodeOptions` — there are currently no format-specific encoding knobs (bitrate, application mode, etc.).

```kotlin
encode {
    options {
        metadataPolicy = MetadataPolicy.PRESERVE   // default: STRIP_ALL
        outputFormat   = OutputFormat.Exact(AudioFormat.Opus)
    }
}
```

## Basic usage

```kotlin
val transmuter = Transmute.audio.to(AudioFormat.Opus)
val opusBytes = transmuter.transmute(inputBytes)
```

## Inspection

```kotlin
val structure  = Transmute.inspect.structure(opusBytes)  // OpusStructure
val inspection = Transmute.inspect.inspect(opusBytes)
// VorbisCommentMetadata carries ARTIST, ALBUM, TITLE, etc.
```

## Notes

- Opus is a modern, low-latency codec particularly well suited for voice and real-time audio.
- The Ogg container carries Vorbis comment metadata in the same way as `.ogg` (Vorbis) files.

## Related

- [Codec API](../codec.md)
- [Plugins](../plugins.md)
- [OGG](ogg.md)
- [Structures](../structures.md)
