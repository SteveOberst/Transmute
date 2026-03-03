# AVIF

| Property | Value |
|----------|-------|
| Constant | `ImageFormat.Avif` |
| MIME type | `image/avif` |
| Extension | `avif` |
| Container | ISOBMFF |

## Platform availability

| Platform | Decode | Encode |
|----------|--------|--------|
| Android  | ✓ | ✓ |
| Desktop  | plugin (GStreamer or libheif) | plugin |
| iOS      | ✓ | ✓ |

## Encode options

```kotlin
HeifEncodeOptions(
    quality: Float = 0.80f,
    metadataPolicy: MetadataPolicy = STRIP_ALL,
    format: ImageFormat = ImageFormat.Avif,
)
```

## Metadata support

AVIF files may carry: `ExifMetadata`, `XmpMetadata`.

## Structure support

`AvifStructure` — ISOBMFF box list.
