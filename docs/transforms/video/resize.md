# Video: resize

Resize video frames to fit within bounds, preserving aspect ratio.

## Factory

```kotlin
Transformers.video().resize(maxWidth: Int, maxHeight: Int)
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `maxWidth` | `Int` | ✅ | Maximum frame width in pixels |
| `maxHeight` | `Int` | ✅ | Maximum frame height in pixels |

## Behaviour

- Frames are scaled to fit within `maxWidth × maxHeight` while preserving aspect ratio.
- Never upscales; if the source is smaller than the bounds, it is left unchanged.
- Output dimensions are always even numbers (required by most video codecs).

## DSL usage

```kotlin
val transmuter = Transmute.video.to(VideoFormat.Mp4) {
    encode {
        pipeline { resize(maxWidth = 1280, maxHeight = 720) }
    }
}
```

## Related

- [crop](crop.md)
- [frameRate](frame-rate.md)
- [Transforms overview](README.md)
