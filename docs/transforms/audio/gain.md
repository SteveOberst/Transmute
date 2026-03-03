# Audio: gain

Apply volume gain or attenuation in decibels.

## Factory

```kotlin
Transformers.audio().gain(db: Float)
```

| Parameter | Type | Required | Range | Description |
|-----------|------|----------|-------|-------------|
| `db` | `Float` | ✅ | −60 … +60 | Gain in decibels. Positive = louder, negative = quieter |

## Behaviour

- Multiplies each sample by `10^(db/20)`.
- Samples are clamped to [−1, 1] after gain application; clipping may occur for large positive values.
- For loudness normalisation without clipping risk, prefer [`normalize`](normalize.md).

## DSL usage

```kotlin
val transmuter = Transmute.audio.to(AudioFormat.Wav) {
    decode {
        pipeline {
            gain(db = -6f)   // cut by 6 dB (half amplitude)
            gain(db = +3f)   // boost by 3 dB
        }
    }
}
```

## Related

- [normalize](normalize.md)
- [compressor](compressor.md)
- [Transforms overview](README.md)
