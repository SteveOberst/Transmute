# Video: speed

Change video playback speed.

## Factory

```kotlin
Transformers.video().speed(speed: Float)
```

| Parameter | Type | Required | Range | Description |
|-----------|------|----------|-------|-------------|
| `speed` | `Float` | ✅ | 0.25 … 4.0 | Speed multiplier (`1.0` = unchanged) |

## Behaviour

- Adjusts both video frame timestamps and audio pitch.
- `0.5` → half speed (slow motion; output is twice as long).
- `2.0` → double speed (time-lapse; output is half as long).
- Audio pitch is adjusted to match the new speed (tempo change, not pitch shift).

## DSL usage

```kotlin
// 2× fast-forward
val transmuter = Transmute.video.to(VideoFormat.Mp4) {
    decode {
        pipeline { speed(2.0f) }
    }
}

// Slow motion at 0.5×
val transmuter = Transmute.video.to(VideoFormat.Mp4) {
    decode {
        pipeline { speed(0.5f) }
    }
}
```

## Related

- [frameRate](frame-rate.md)
- [trim](trim.md)
- [Transforms overview](README.md)
