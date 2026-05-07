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
| Android  | built-in | built-in |
| Desktop  | built-in | built-in |
| iOS      | built-in | built-in |

## Encode parameters

```kotlin
ImageParamKeys.WebpQuality           // Float, default 0.80f
ImageParamKeys.WebpLossless          // Boolean, default false
ImageParamKeys.EncodeMetadataPolicy  // MetadataPolicy, default STRIP_ALL
ImageParamKeys.OutputFormat          // OutputFormat<ImageFormat>, default ORIGINAL
```

## Metadata support

WebP files may carry: `ExifMetadata`, `XmpMetadata`, `IccProfileMetadata`.

## Structure support

`WebpStructure` — RIFF/WEBP chunk list.



