# Image: crop

Crop to a rectangular sub-region.

## Factory

```kotlin
Transformers.image().crop(x: Int, y: Int, width: Int, height: Int)
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `x` | `Int` | ✅ | X offset from the left edge in pixels |
| `y` | `Int` | ✅ | Y offset from the top edge in pixels |
| `width` | `Int` | ✅ | Crop width in pixels |
| `height` | `Int` | ✅ | Crop height in pixels |

## Behaviour

- Coordinates are clamped to image bounds; no error is thrown for out-of-bounds regions.
- The resulting image has the dimensions `width × height` (or smaller if clamped).

## DSL usage

```kotlin
val transmuter = Transmute.image {
    decode {
        pipeline {
            crop(x = 100, y = 50, width = 640, height = 480)
        }
    }
}
```

## Programmatic usage

```kotlin
Transformers.image().crop(x = 0, y = 0, width = 256, height = 256)
```

## Related

- [scale](scale.md)
- [resize](resize.md)
- [Transforms overview](README.md)
