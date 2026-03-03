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
| Android  | ✓ (decode) | – |
| Desktop  | ✓ | ✓ |
| iOS      | ✓ (decode) | – |

## Encode options

No format-specific options. Use `CanonicalImageEncodeOptions` if you need to set `metadataPolicy`.

## Metadata support

No metadata extraction. GIF does not carry EXIF or XMP in a standard Transmute-parseable form.

## Structure support

`GifStructure` — GIF header, logical screen descriptor, and frame list.
