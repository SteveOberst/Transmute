# Speed

Change playback speed without altering pitch.

## Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| speed | Float | - | Speed multiplier; >1 = faster, <1 = slower |

## Usage

### DSL

```kotlin
import dev.transmute.core.asBytes
Transmute.audio { speed(1.5f) }.transmute(bytes.asBytes()).bytes.data
```

### Pipeline

```kotlin
transform { add(Transformers.audio().speed(1.5f)) }
```

## Notes

- Uses SOLA (Synchronous Overlap-Add) time-stretching to change tempo while preserving pitch.
- `speed = 2.0` halves the duration; `speed = 0.5` doubles it.
- Extreme values (e.g., <0.25 or >4.0) may introduce audible artifacts.
