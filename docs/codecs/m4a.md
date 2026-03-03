# M4A

`AudioFormat.M4a` — MPEG-4 Audio

| Property | Value |
|----------|-------|
| Enum value | `AudioFormat.M4a` |
| MIME type | `audio/mp4` |
| Extension | `.m4a` |
| Container | ISOBMFF (MP4) |
| Metadata | `ItunesMetadata` |
| Structure | `M4aStructure` |

## Platform support

| Platform | Decode | Encode |
|----------|--------|--------|
| Android | ✅ built-in (MediaCodec) | ✅ built-in |
| Desktop (JVM) | ⚠️ GStreamer plugin | ⚠️ GStreamer plugin |
| iOS | ✅ built-in | ✅ built-in |

On Desktop, M4A requires the [GStreamer plugin](../plugins.md).

## Desktop plugin setup

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

M4A uses `CanonicalAudioEncodeOptions` — there are currently no format-specific encoding knobs.

```kotlin
encode {
    options {
        metadataPolicy = MetadataPolicy.PRESERVE   // default: STRIP_ALL
        outputFormat   = OutputFormat.Exact(AudioFormat.M4a)
    }
}
```

## Basic usage

```kotlin
val transmuter = Transmute.audio.to(AudioFormat.M4a)
val m4aBytes = transmuter.transmute(inputBytes)
```

## Inspection

```kotlin
val structure  = Transmute.inspect.structure(m4aBytes)  // M4aStructure
val inspection = Transmute.inspect.inspect(m4aBytes)
// ItunesMetadata carries iTunes-style atoms: artist, album, cover art, etc.
```

## Notes

- M4A is an audio-only MP4 container, commonly using AAC audio internally.
- iTunes metadata (`©nam`, `©ART`, `©alb`, `covr`, etc.) is preserved when `metadataPolicy = MetadataPolicy.PRESERVE`.

## Related

- [Codec API](../codec.md)
- [Plugins](../plugins.md)
- [AAC](aac.md)
- [MP4](mp4.md)
