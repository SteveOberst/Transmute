# BMP

| Property | Value |
|----------|-------|
| Constant | `ImageFormat.Bmp` |
| MIME type | `image/bmp` |
| Extension | `bmp` |
| Container | DIB |

BMP is implemented in **pure Kotlin** and works on all platforms without any native dependencies.

## Platform availability

| Platform | Decode | Encode |
|----------|--------|--------|
| Android  | ✓ | ✓ |
| Desktop  | ✓ | ✓ |
| iOS      | ✓ | ✓ |

## Encode options

No format-specific options.

## Metadata support

No metadata extraction.

## Structure support

`BmpStructure` — DIB file header, info header, and pixel data summary.
