# WebP

| Property | Value |
|----------|-------|
| Constant | `ImageFormat.Webp` |
| MIME type | `image/webp` |
| Extension | `webp` |
| Container | RIFF |

## Platform availability

| Platform | Decode | Encode |
|----------|--------|--------|
| Android  | ✓ | ✓ |
| Desktop  | ✓ | ✓ |
| iOS      | ✓ | ✓ |

## Encode options

```kotlin
WebPEncodeOptions(
    quality: Float = 0.80f,          // 0.0 – 1.0 (lossy mode)
    lossless: Boolean = false,       // true = lossless (ignores quality)
    metadataPolicy: MetadataPolicy = STRIP_ALL,
)
```

## Metadata support

WebP files may carry: `ExifMetadata`, `XmpMetadata`, `IccProfileMetadata`.

## Structure support

`WebpStructure` — RIFF/WEBP chunk list.
