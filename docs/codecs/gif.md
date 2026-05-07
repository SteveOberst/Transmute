# GIF

| Property | Value |
|----------|-------|
| Constant | `ImageFormat.Gif` |
| MIME type | `image/gif` |
| Extension | `gif` |
| Container | GIF |

## Platform availability

| Platform | Decode | Encode |
|----------|--------|--------|
| Android  | built-in | no |
| Desktop  | built-in | built-in |
| iOS      | built-in | no |

## Encode parameters

GIF has no format-specific parameter keys today. Use `ImageParamKeys.EncodeMetadataPolicy`
if you need to preserve metadata during encoding.

## Metadata support

No metadata extraction. GIF does not carry EXIF or XMP in a standard Transmute-parseable form.

## Structure support

`GifStructure` — GIF header, logical screen descriptor, and frame list.



