# Image: blur

Apply a box blur.

## Factory

```kotlin
Transformers.image().blur(radius: Int = 1)
```

| Parameter | Type | Default | Range | Description |
|-----------|------|---------|-------|-------------|
| `radius` | `Int` | `1` | 1 … 20 | Blur radius. Kernel size = `(2 * radius + 1)²` |

## Behaviour

- Applies a box (average) blur over a `(2r+1) × (2r+1)` kernel.
- `radius = 1` → 3×3 kernel; `radius = 2` → 5×5; `radius = 10` → 21×21.
- Edge pixels are handled with clamping.

## DSL usage

```kotlin
val transmuter = Transmute.image {
    decode {
        pipeline {
            blur(radius = 3)   // noticeable blur
        }
    }
}
```

## Related

- [brightnessContrast](brightness-contrast.md)
- [Transforms overview](README.md)
