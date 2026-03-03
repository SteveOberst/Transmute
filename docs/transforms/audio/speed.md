# Audio: speed

Change playback speed without altering pitch.

## Factory

```kotlin
Transformers.audio().speed(speed: Float)
```

| Parameter | Type | Required | Range | Description |
|-----------|------|----------|-------|-------------|
| `speed` | `Float` | ✅ | 0.25 … 4.0 | Speed multiplier (`1.0` = unchanged) |

## Behaviour

- Uses SOLA (Synchronised Overlap-Add) time-stretching to adjust duration without changing pitch.
- `0.5` → half speed (audio is twice as long).
- `2.0` → double speed (audio is half as long).

## DSL usage

```kotlin
val transmuter = Transmute.audio.to(AudioFormat.Mp3) {
    decode {
        pipeline { speed(1.5f) }   // 50% faster
    }
}
```

## Related

- [resample](resample.md)
- [trim](trim.md)
- [Transforms overview](README.md)
