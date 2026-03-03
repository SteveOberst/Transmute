# MP3

`AudioFormat.Mp3` — MPEG-1/2 Audio Layer III

| Property | Value |
|----------|-------|
| Enum value | `AudioFormat.Mp3` |
| MIME type | `audio/mpeg` |
| Extension | `.mp3` |
| Container | MPEG stream |
| Metadata | `Id3v1Metadata`, `Id3v2Metadata` |
| Structure | `Mp3Structure` |

## Platform support

| Platform | Decode | Encode |
|----------|--------|--------|
| Android | ✅ built-in (MediaCodec) | ✅ built-in |
| Desktop (JVM) | ✅ built-in | ✅ built-in |
| iOS | ✅ built-in | ✅ built-in |

MP3 is the most universally supported audio format. No plugins are required on any platform.

## Encode options

MP3 uses `CanonicalAudioEncodeOptions` — there are currently no format-specific encoding knobs (bitrate, VBR settings, etc.).

```kotlin
encode {
    options {
        metadataPolicy = MetadataPolicy.PRESERVE   // default: STRIP_ALL
        outputFormat   = OutputFormat.Exact(AudioFormat.Mp3)
    }
}
```

## Basic usage

```kotlin
// Transcode any audio to MP3
val transmuter = Transmute.audio.to(AudioFormat.Mp3) {
    encode { options { metadataPolicy = MetadataPolicy.PRESERVE } }
}
val mp3Bytes = transmuter.transmute(inputBytes)

// Dynamic output — keep existing bytes as MP3
val dynamic = Transmute.audio {
    encode { options { outputFormat = OutputFormat.Exact(AudioFormat.Mp3) } }
}
val result = dynamic.transmute(inputBytes)
```

## Inspection

```kotlin
// Read ID3 tags
val inspection = Transmute.inspect.inspect(mp3Bytes)
val tags = inspection.metadata

// Read file structure (frame headers, bitrate info, etc.)
val structure = Transmute.inspect.structure(mp3Bytes) // Mp3Structure
```

## Related

- [Codec API](../codec.md)
- [Inspection](../inspect.md)
- [Structures](../structures.md)
- [Pipelines](../pipelines.md)
