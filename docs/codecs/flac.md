# FLAC

`AudioFormat.Flac` — Free Lossless Audio Codec

| Property | Value |
|----------|-------|
| Enum value | `AudioFormat.Flac` |
| MIME type | `audio/flac` |
| Extension | `.flac` |
| Container | FLAC native |
| Metadata | `VorbisCommentMetadata` |
| Structure | `FlacStructure` |

## Platform support

| Platform | Decode | Encode |
|----------|--------|--------|
| Android | ✅ built-in (MediaCodec) | ✅ built-in |
| Desktop (JVM) | ✅ built-in (decode only) | ⚠️ GStreamer plugin |
| iOS | ✅ built-in | ✅ built-in |

On Desktop, FLAC decoding is available built-in; encoding requires the [GStreamer plugin](../plugins.md).

## Desktop plugin setup (encode only)

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

FLAC uses `CanonicalAudioEncodeOptions` — there are currently no format-specific encoding knobs (compression level, etc.).

```kotlin
encode {
    options {
        metadataPolicy = MetadataPolicy.PRESERVE   // default: STRIP_ALL
        outputFormat   = OutputFormat.Exact(AudioFormat.Flac)
    }
}
```

## Basic usage

```kotlin
// Decode FLAC to PCM-backed intermediate, re-encode as FLAC
val transmuter = Transmute.audio.to(AudioFormat.Flac) {
    encode { options { metadataPolicy = MetadataPolicy.PRESERVE } }
}
val flacBytes = transmuter.transmute(inputBytes)
```

## Inspection

```kotlin
val structure  = Transmute.inspect.structure(flacBytes)  // FlacStructure
val inspection = Transmute.inspect.inspect(flacBytes)
// VorbisCommentMetadata carries ARTIST, ALBUM, TITLE, etc.
```

## Notes

- FLAC is lossless; the re-encoded output is bit-for-bit equivalent to the source audio, not necessarily to the original FLAC file (encoder parameters may differ).

## Related

- [Codec API](../codec.md)
- [Plugins](../plugins.md)
- [Structures](../structures.md)
