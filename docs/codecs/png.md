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
| Android  | built-in | built-in |
| Desktop  | built-in | built-in |
| iOS      | built-in | built-in |

## Encode parameters

```kotlin
ImageParamKeys.PngCompressionLevel   // Int, default 6
ImageParamKeys.EncodeMetadataPolicy  // MetadataPolicy, default STRIP_ALL
ImageParamKeys.OutputFormat          // OutputFormat<ImageFormat>, default ORIGINAL
```

Usage:

```kotlin
transmute().image.to(ImageFormat.Png) {
    encode {
        params(Params.of(ImageParamKeys.PngCompressionLevel to 9))
    }
}.transmute(source)
```

## Metadata support

PNG files may carry: `PngTextMetadata`, `XmpMetadata`.

## Structure support

| Type | Description |
|------|-------------|
| `PngStructure` | IHDR, IDAT, PLTE, and all named chunks |

```kotlin
val s = transmute().inspect.structure(bytes, ImageFormat.Png) as PngStructure?
println("Width:  ${s?.ihdr?.width}")
println("Height: ${s?.ihdr?.height}")
```



