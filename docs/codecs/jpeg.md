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
| Android  | ✓ | ✓ |
| Desktop  | ✓ | ✓ |
| iOS      | ✓ | ✓ |

## Encode options

```kotlin
JpegEncodeOptions(
    quality: Float = 0.85f,                          // 0.0 (worst) – 1.0 (best)
    metadataPolicy: MetadataPolicy = STRIP_ALL,
)
```

Usage:

```kotlin
Transmute.image.to(ImageFormat.Jpeg) {
    encode { options(JpegEncodeOptions(quality = 0.92f)) }
}.transmute(source)
```

## Metadata support

JPEG files may carry: `ExifMetadata`, `XmpMetadata`, `IccProfileMetadata`.

## Structure support

| Type | Description |
|------|-------------|
| `JpegStructure` | JPEG segment list |

```kotlin
val s = Transmute.inspect.structure(bytes, ImageFormat.Jpeg) as JpegStructure?
println("Segments: ${s?.segments?.size}")
```
