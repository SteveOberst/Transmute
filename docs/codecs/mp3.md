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
| Android | built-in (MediaCodec) | built-in |
| Desktop (JVM) | built-in | built-in |
| iOS | built-in | built-in |

MP3 is the most universally supported audio format. No plugins are required on any platform.

## Encode options

MP3 has no format-specific parameter keys today. Use `AudioParamKeys.OutputFormat`
and `AudioParamKeys.EncodeMetadataPolicy` when you need to force MP3 output or preserve metadata.

```kotlin
encode {
    params(
        Params.of(
            AudioParamKeys.EncodeMetadataPolicy to MetadataPolicy.PRESERVE,
            AudioParamKeys.OutputFormat to OutputFormat.Exact(AudioFormat.Mp3),
        )
    )
}
```

## Basic usage

```kotlin
// Transcode any audio to MP3
val transmuter = transmute().audio.to(AudioFormat.Mp3) {
    encode {
        params(Params.of(AudioParamKeys.EncodeMetadataPolicy to MetadataPolicy.PRESERVE))
    }
}
val mp3Bytes = transmuter.transmute(inputBytes)

// Dynamic output — keep existing bytes as MP3
val dynamic = transmute().audio {
    encode {
        params(Params.of(AudioParamKeys.OutputFormat to OutputFormat.Exact(AudioFormat.Mp3)))
    }
}
val result = dynamic.transmute(inputBytes)
```

## Inspection

```kotlin
// Read ID3 tags
val inspection = transmute().inspect.inspect(mp3Bytes)
val tags = inspection.metadata.filterIsInstance<Id3v2Metadata>().firstOrNull()

// Read file structure (frame headers, bitrate info, etc.)
val structure = transmute().inspect.structure(mp3Bytes) // Mp3Structure
```

## Related

- [Codec API](../codec.md)
- [Inspection](../inspect.md)
- [Structures](../structures.md)
- [Pipelines](../pipelines.md)



