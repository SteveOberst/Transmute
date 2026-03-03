# Image: resize

Resize to exact dimensions with an optional resampling filter.

## Factory

```kotlin
Transformers.image().resize(
    targetWidth: Int,
    targetHeight: Int,
    filter: ResampleFilter = ResampleFilter.BICUBIC_MITCHELL,
    allowUpscale: Boolean = true,
)
```

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `targetWidth` | `Int` | ✅ | — | Target width in pixels |
| `targetHeight` | `Int` | ✅ | — | Target height in pixels |
| `filter` | `ResampleFilter` | | `BICUBIC_MITCHELL` | Resampling algorithm |
| `allowUpscale` | `Boolean` | | `true` | If `false`, images smaller than the target are not scaled up |

### Available resample filters

| Value | Notes |
|-------|-------|
| `BICUBIC_MITCHELL` | Default. High quality, moderate cost |
| `BILINEAR` | Faster, slightly lower quality |
| `NEAREST_NEIGHBOR` | Fastest, blocky for photos |
| `LANCZOS` | Highest quality, most expensive |

## Behaviour

- **Does not preserve aspect ratio.** The image is stretched/squashed to the exact target dimensions.
- Use [`scale`](scale.md) if you want aspect-ratio-preserving downscaling.

## DSL usage

```kotlin
val transmuter = Transmute.image {
    decode {
        pipeline {
            resize(targetWidth = 800, targetHeight = 600)
        }
    }
}
```

## No-upscale example

```kotlin
Transformers.image().resize(
    targetWidth  = 400,
    targetHeight = 300,
    allowUpscale = false,
)
```

## Related

- [scale](scale.md)
- [crop](crop.md)
- [Transforms overview](README.md)
