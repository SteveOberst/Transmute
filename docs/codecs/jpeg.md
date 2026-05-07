# JPEG

| Property | Value |
|----------|-------|
| Constant | `ImageFormat.Jpeg` |
| MIME type | `image/jpeg` |
| Extension | `jpg` |
| Container | JFIF |

## Platform availability

| Platform | Decode | Encode |
|----------|--------|--------|
| Android  | built-in | built-in |
| Desktop  | built-in | built-in |
| iOS      | built-in | built-in |

## Encode parameters

```kotlin
ImageParamKeys.JpegQuality           // Float, default 0.85f
ImageParamKeys.EncodeMetadataPolicy  // MetadataPolicy, default STRIP_ALL
ImageParamKeys.OutputFormat          // OutputFormat<ImageFormat>, default ORIGINAL
```

Usage:

```kotlin
transmute().image.to(ImageFormat.Jpeg) {
    encode {
        params(Params.of(ImageParamKeys.JpegQuality to 0.92f))
    }
}.transmute(source)
```

## Metadata support

JPEG files may carry: `ExifMetadata`, `XmpMetadata`, `IccProfileMetadata`.

## Structure support

| Type | Description |
|------|-------------|
| `JpegStructure` | JPEG segment list |

```kotlin
val s = transmute().inspect.structure(bytes, ImageFormat.Jpeg) as JpegStructure?
println("Segments: ${s?.segments?.size}")
```



