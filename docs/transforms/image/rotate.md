# Image: rotate

Rotate clockwise by 90°, 180°, or 270°.

## Factory

```kotlin
Transformers.image().rotate(degrees: Int = 90)
```

| Parameter | Type | Required | Default | Allowed values |
|-----------|------|----------|---------|---------------|
| `degrees` | `Int` | | `90` | `90`, `180`, `270` |

## Behaviour

- Rotation is always clockwise.
- Canvas dimensions are swapped for 90° and 270° (portrait ↔ landscape).
- Only 90°, 180°, and 270° are supported; other values are rejected.

## DSL usage

```kotlin
val transmuter = Transmute.image {
    decode {
        pipeline {
            rotate(degrees = 90)   // 90° clockwise
        }
    }
}
```

## Common use-case: fix EXIF orientation

Many cameras embed an EXIF orientation tag instead of physically rotating pixels. Extract that tag from the inspection result and apply the matching rotation:

```kotlin
val inspection = Transmute.inspect.inspect(bytes)
// determine rotation from inspection.metadata ExifMetadata.orientation
// then build a transmuter with the matching rotate() call
```

## Related

- [flip](flip.md)
- [Transforms overview](README.md)
