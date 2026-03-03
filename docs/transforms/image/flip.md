# Image: flip

Flip the image horizontally, vertically, or both.

## Factory

```kotlin
Transformers.image().flip(horizontal: Boolean = false, vertical: Boolean = false)
```

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `horizontal` | `Boolean` | `false` | Mirror left↔right |
| `vertical` | `Boolean` | `false` | Mirror top↔bottom |

## Behaviour

- Both `horizontal` and `vertical` may be `true` simultaneously (equivalent to a 180° rotation).
- If both are `false` the transform is a no-op.

## DSL usage

```kotlin
val transmuter = Transmute.image {
    decode {
        pipeline {
            flip(horizontal = true)          // mirror
            flip(vertical   = true)          // upside-down
            flip(horizontal = true, vertical = true)  // both
        }
    }
}
```

## Related

- [rotate](rotate.md)
- [Transforms overview](README.md)
