# transmute-image

Image domain module — formats, codecs, intermediate representation, and transforms.

## Overview

Defines image-specific formats, the `ImageIR` intermediate representation,
pixel-level transforms with resampling kernels, and platform-specific codecs.
BMP support is built-in on all platforms; desktop JVM adds WebP and TIFF via
TwelveMonkeys ImageIO.

## Key Types

### Formats & IR

| Type | Purpose |
|------|---------|
| `ImageFormat` | Sealed interface: `Jpeg`, `Png`, `Webp`, `Heif`, `Heic`, `Avif`, `Gif`, `Bmp`, `Tiff`, `Unknown` |
| `ImageIR` | Intermediate representation (buffer, width, height, stride, pixelFormat, alpha, colorInfo, orientation) |
| `PixelBuffer` / `ByteArrayPixelBuffer` | Pixel data storage |
| `PixelFormat` | Enum: `RGBA_8888`, `RGB_888`, `RGBA_F16`, `RGBA_F32` |
| `AlphaSemantics` | Enum: `STRAIGHT`, `PREMULTIPLIED`, `OPAQUE` |
| `ColorInfo` | Colorspace, transfer function, ICC profile |
| `ImageMetadata` | EXIF blob, XMP blob, app metadata |

### Codecs

| Type | Purpose |
|------|---------|
| `ImageCodec` / `ImageDecoder` / `ImageEncoder` | Codec interfaces |
| `ImageDecoderRegistry` / `ImageEncoderRegistry` | Registries with mutable variants for plugins |
| `ImageDecodeOptions` / `ImageEncodeOptions` | Sealed option hierarchies |
| `ImageFormatDetector` | Format detection from bytes |

### Transforms

| Transform | Purpose |
|-----------|---------|
| `ImageScaleTransform` | Scale by factor |
| `ImageResizeTransform` | Resize to target dimensions |
| `ImageCropTransform` | Crop to rectangle |
| `ImageRotateTransform` | Rotate by 90°, 180°, or 270° clockwise |
| `ImageGrayscaleTransform` | Convert to grayscale |
| `ImageFlipTransform` | Flip horizontal/vertical |
| `ImageBrightnessContrastTransform` | Adjust brightness and contrast |
| `ImageBlurTransform` | Box blur |
| `ImageOpacityTransform` | Adjust opacity |

### Resample Kernels

`ResampleFilter`, `ResampleKernel`, `ResampleFactory` — implementations:
Bicubic Mitchell, Bilinear, Box, Lanczos3, Nearest.

## Dependencies

- `transmute-codec`
- `kotlinx-coroutines-core`

## Targets

Android, Desktop JVM, iOS — via Kotlin Multiplatform.
