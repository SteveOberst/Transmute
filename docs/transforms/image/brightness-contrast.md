# Image: brightnessContrast

Adjust brightness and/or contrast of an image.

## Factory

```kotlin
Transformers.image().brightnessContrast(brightness: Float = 0f, contrast: Float = 1f)
```

| Parameter | Type | Default | Range | Description |
|-----------|------|---------|-------|-------------|
| `brightness` | `Float` | `0f` | −255 … +255 | Additive brightness offset per channel |
| `contrast` | `Float` | `1f` | 0 … 3 | Contrast multiplier around the midpoint (128) |

## Behaviour

**Brightness** adds a flat offset to each colour channel:
- Positive values → brighter
- Negative values → darker
- 0 → no change

**Contrast** scales channel values around the midpoint (128):
- `0` → uniform grey
- `1` → no change
- `> 1` → increased contrast

Both adjustments are clamped to [0, 255]; overflow is not wrapped.

## DSL usage

```kotlin
val transmuter = Transmute.image {
    decode {
        pipeline {
            brightnessContrast(brightness = 30f, contrast = 1.2f)
        }
    }
}
```

## Related

- [grayscale](grayscale.md)
- [opacity](opacity.md)
- [Transforms overview](README.md)
