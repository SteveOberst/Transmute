# Resize

Resize an image to exact dimensions with a configurable resample filter.

## Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| targetWidth | Int | — | Desired output width in pixels |
| targetHeight | Int | — | Desired output height in pixels |
| filter | ResampleFilter | LANCZOS3 | Resampling filter algorithm |
| allowUpscale | Boolean | false | Whether to upscale if source is smaller |

### ResampleFilter options

| Filter | Description |
|--------|-------------|
| NEAREST | Nearest-neighbor, fastest, blocky |
| BILINEAR | Bilinear interpolation |
| BICUBIC_MITCHELL | Mitchell-Netravali cubic |
| CATMULL_ROM | Catmull-Rom cubic |
| LANCZOS3 | Lanczos windowed sinc, 3-lobe |

## Usage

### DSL

```kotlin
Transmute.image(bytes) { resize(800, 600, filter = ResampleFilter.LANCZOS3) }
```

### Pipeline

```kotlin
transform { add(Transformers.image().resize(800, 600, ResampleFilter.LANCZOS3)) }
```

## Notes

- Kernel-based filtering with anti-aliasing applied automatically on downscale.
- `allowUpscale = false` by default — images smaller than the target are returned unchanged.
- For aspect-ratio-preserving resize, use `scale` instead.
