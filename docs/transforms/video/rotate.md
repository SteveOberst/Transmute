# Video: rotate

Rotate video frames clockwise by 90°, 180°, or 270°.

## Factory

```kotlin
Transformers.video().rotate(degrees: Int)
```

| Parameter | Type | Required | Allowed values | Description |
|-----------|------|----------|---------------|-------------|
| `degrees` | `Int` | ✅ | `90`, `180`, `270` | Clockwise rotation angle |

## Behaviour

- Applied to every frame; does not modify audio.
- Canvas dimensions are swapped for 90° and 270° (portrait ↔ landscape).
- Only 90°, 180°, and 270° are supported.

## DSL usage

```kotlin
// Fix portrait video recorded in landscape orientation
val transmuter = Transmute.video.to(VideoFormat.Mp4) {
    decode {
        pipeline { rotate(degrees = 90) }
    }
}
```

## Related

- [crop](crop.md)
- [resize](resize.md)
- [Transforms overview](README.md)
