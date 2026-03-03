# Audio: compressor

Apply dynamic range compression.

## Factory

```kotlin
Transformers.audio().compressor(
    thresholdDb: Float = -20f,
    ratio: Float = 4f,
    attackMs: Float = 10f,
    releaseMs: Float = 100f,
    makeupGainDb: Float = 0f,
)
```

| Parameter | Type | Default | Range | Description |
|-----------|------|---------|-------|-------------|
| `thresholdDb` | `Float` | `-20f` | −80 … 0 | Level above which compression is applied |
| `ratio` | `Float` | `4f` | 1.0 … 20.0 | Compression ratio (e.g. `4` = 4:1) |
| `attackMs` | `Float` | `10f` | 0.1 … 500 | Time to engage compression after threshold is exceeded |
| `releaseMs` | `Float` | `100f` | 1 … 2000 | Time to disengage compression after signal drops below threshold |
| `makeupGainDb` | `Float` | `0f` | −20 … +20 | Gain applied after compression to restore loudness |

## Behaviour

- Signals above `thresholdDb` are reduced by `ratio`:1; a `ratio` of `1` is no compression.
- High `ratio` settings (> 10) approximate limiting behaviour.
- `attackMs` and `releaseMs` use an exponential envelope.

## DSL usage

```kotlin
val transmuter = Transmute.audio.to(AudioFormat.Mp3) {
    decode {
        pipeline {
            compressor(
                thresholdDb  = -18f,
                ratio        = 3f,
                makeupGainDb = 4f,
            )
        }
    }
}
```

## Related

- [normalize](normalize.md)
- [gain](gain.md)
- [Transforms overview](README.md)
