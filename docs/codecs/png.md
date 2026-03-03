# PNG

| Property | Value |
|----------|-------|
| Constant | `ImageFormat.Png` |
| MIME type | `image/png` |
| Extension | `png` |
| Container | PNG chunks |

## Platform availability

| Platform | Decode | Encode |
|----------|--------|--------|
| Android  | ✓ | ✓ |
| Desktop  | ✓ | ✓ |
| iOS      | ✓ | ✓ |

## Encode options

```kotlin
PngEncodeOptions(
    compressionLevel: Int = 6,       // 0 (no compression) – 9 (max compression)
    metadataPolicy: MetadataPolicy = STRIP_ALL,
)
```

Usage:

```kotlin
Transmute.image.to(ImageFormat.Png) {
    encode { options(PngEncodeOptions(compressionLevel = 9)) }
}.transmute(source)
```

## Metadata support

PNG files may carry: `PngTextMetadata`, `XmpMetadata`.

## Structure support

| Type | Description |
|------|-------------|
| `PngStructure` | IHDR, IDAT, PLTE, and all named chunks |

```kotlin
val s = Transmute.inspect.structure(bytes, ImageFormat.Png) as PngStructure?
println("Width:  ${s?.ihdr?.width}")
println("Height: ${s?.ihdr?.height}")
```
