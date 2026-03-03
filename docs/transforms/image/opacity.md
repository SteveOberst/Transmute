# Image: opacity

Adjust the alpha channel opacity.

## Factory

```kotlin
Transformers.image().opacity(opacity: Float)
```

| Parameter | Type | Required | Range | Description |
|-----------|------|----------|-------|-------------|
| `opacity` | `Float` | ✅ | 0.0 … 1.0 | Target opacity (`1.0` = unchanged, `0.0` = fully transparent) |

## Behaviour

- Scales each pixel's alpha channel by `opacity`.
- If the source format has no alpha channel (e.g. JPEG), the alpha is treated as fully opaque (255) before scaling.
- Output formats that do not support transparency (JPEG, BMP) will discard alpha on encode; use PNG, WebP, or HEIF for transparent results.

## DSL usage

```kotlin
val transmuter = Transmute.image.to(ImageFormat.Png) {
    decode {
        pipeline {
            opacity(0.5f)   // 50% transparent
        }
    }
}
```

## Related

- [grayscale](grayscale.md)
- [brightnessContrast](brightness-contrast.md)
- [Transforms overview](README.md)
