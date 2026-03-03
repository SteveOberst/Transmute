# Audio: resample

Resample to a different sample rate.

## Factory

```kotlin
Transformers.audio().resample(targetSampleRate: Int)
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `targetSampleRate` | `Int` | ✅ | Target sample rate in Hz (e.g. `44100`, `48000`) |

## Behaviour

- Uses linear interpolation for the resampled output.
- If the source is already at `targetSampleRate`, the transform is a no-op.

## DSL usage

```kotlin
val transmuter = Transmute.audio.to(AudioFormat.Wav) {
    decode {
        pipeline { resample(targetSampleRate = 44100) }
    }
}
```

## Common rates

| Value | Use case |
|-------|----------|
| 8000 | Telephony |
| 22050 | Low-quality audio |
| 44100 | CD quality |
| 48000 | Professional / broadcast |

## Related

- [Transforms overview](README.md)
