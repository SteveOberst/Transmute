# Image: scale

Proportionally scale to fit within bounds, preserving aspect ratio. Never upscales.

## Factory

```kotlin
Transformers.image().scale(maxWidth: Int, maxHeight: Int)
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `maxWidth` | `Int` | ✅ | Maximum output width in pixels |
| `maxHeight` | `Int` | ✅ | Maximum output height in pixels |

## Behaviour

- If the image already fits within the bounds, it is returned unchanged.
- Aspect ratio is always preserved; neither dimension ever exceeds its bound.
- Never upscales (use `resize` with `allowUpscale = true` for that).

## DSL usage

```kotlin
val transmuter = Transmute.image {
    decode {
        pipeline { scale(maxWidth = 1920, maxHeight = 1080) }
    }
}
```

## Programmatic usage

```kotlin
val transmuter = Transmute.image {
    decode {
        transform { add(Transformers.image().scale(1920, 1080)) }
    }
}
```

## Comparison with resize

| | `scale` | `resize` |
|-|---------|---------|
| Preserves aspect ratio | ✅ | ❌ (stretches) |
| Prevents upscaling | ✅ | Configurable (`allowUpscale`) |
| Resample filter | No | Configurable |

## Related

- [resize](resize.md)
- [crop](crop.md)
- [Transforms overview](README.md)
