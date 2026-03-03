# Video: crop

Crop video frames to a rectangular sub-region.

## Factory

```kotlin
Transformers.video().crop(x: Int, y: Int, width: Int, height: Int)
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `x` | `Int` | ✅ | X offset from the left edge in pixels |
| `y` | `Int` | ✅ | Y offset from the top edge in pixels |
| `width` | `Int` | ✅ | Crop width in pixels |
| `height` | `Int` | ✅ | Crop height in pixels |

## Behaviour

- Applies the crop to every frame.
- Coordinates are clamped to frame bounds.
- Output dimensions are the requested `width × height` (or smaller if clamped).
- Width and height are rounded down to the nearest even number if required by the codec.

## DSL usage

```kotlin
val transmuter = Transmute.video.to(VideoFormat.Mp4) {
    decode {
        pipeline {
            crop(x = 0, y = 140, width = 1920, height = 800)  // 21:9 crop
        }
    }
}
```

## Related

- [resize](resize.md)
- [rotate](rotate.md)
- [Transforms overview](README.md)
