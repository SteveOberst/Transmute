# Speed (Video)

Change playback speed, adjusting both frame timing and audio.

## Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| speed | Float | - | Speed multiplier; >1 = faster, <1 = slower |

## Usage

### DSL

```kotlin
Transmute.video { speed(2.0f) }.transmute(bytes.asBytes()).bytes.data
```

### Pipeline

```kotlin
transform { add(Transformers.video().speed(2.0f)) }
```

## Notes

- Both video frame timestamps and audio samples are adjusted.
- Audio pitch changes proportionally (no time-stretch).
- `speed = 2.0` halves the duration; `speed = 0.5` doubles it.
