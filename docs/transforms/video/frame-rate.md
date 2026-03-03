# Video: frameRate

Change the frame rate of a video.

## Factory

```kotlin
Transformers.video().frameRate(targetFps: Double)
```

| Parameter | Type | Required | Range | Description |
|-----------|------|----------|-------|-------------|
| `targetFps` | `Double` | ✅ | 1 … 240 | Target frames per second |

## Behaviour

- Adjusts the presentation timestamps of frames to match the target frame rate.
- Frame dropping or duplication is applied as needed (nearest-frame strategy).
- Audio is not affected.

## DSL usage

```kotlin
val transmuter = Transmute.video.to(VideoFormat.Mp4) {
    encode {
        pipeline { frameRate(30.0) }
    }
}
```

## Common values

| FPS | Use case |
|-----|----------|
| 24 | Cinema |
| 25 | PAL broadcast |
| 30 | NTSC broadcast / social media |
| 60 | Gaming / high motion |

## Related

- [resize](resize.md)
- [speed](speed.md)
- [Transforms overview](README.md)
