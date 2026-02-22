# Frame Rate

Change the frame rate of a video.

## Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| targetFps | Double | - | Desired output frames per second |

## Usage

### DSL

```kotlin
Transmute.video { frameRate(targetFps = 30.0) }.transmute(bytes).bytes
```

### Pipeline

```kotlin
transform { add(Transformers.video().frameRate(30.0)) }
```

## Notes

- Frames are dropped or duplicated to match the target rate.
- Audio track timing is unaffected (duration stays the same).
- No motion interpolation - only nearest-frame selection.
