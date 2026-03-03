# Audio: normalize

Normalize peak amplitude to a target level.

## Factory

```kotlin
Transformers.audio().normalize(targetPeak: Float = 0.95f)
```

| Parameter | Type | Default | Range | Description |
|-----------|------|---------|-------|-------------|
| `targetPeak` | `Float` | `0.95f` | 0.0 … 1.0 | Target peak amplitude as a linear value |

## Behaviour

- Scans the entire track for the maximum absolute sample value.
- Scales all samples by `targetPeak / maxSample`.
- If the audio is silence (maxSample = 0), no scaling is applied.

## DSL usage

```kotlin
val transmuter = Transmute.audio.to(AudioFormat.Mp3) {
    decode {
        pipeline { normalize() }                   // default: 0.95
    }
}

val transmuter = Transmute.audio {
    decode {
        pipeline { normalize(targetPeak = 0.9f) }
    }
}
```

## Related

- [gain](gain.md)
- [compressor](compressor.md)
- [Transforms overview](README.md)
