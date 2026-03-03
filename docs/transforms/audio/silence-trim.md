# Audio: silenceTrim

Remove silence from the start and/or end of audio.

## Factory

```kotlin
Transformers.audio().silenceTrim(
    thresholdDb: Float = -40f,
    minSilenceMs: Long = 100,
    trimStart: Boolean = true,
    trimEnd: Boolean = true,
)
```

| Parameter | Type | Default | Range | Description |
|-----------|------|---------|-------|-------------|
| `thresholdDb` | `Float` | `-40f` | −80 … 0 | Level below which audio is considered silence |
| `minSilenceMs` | `Long` | `100` | ≥ 0 | Minimum continuous silence required before trimming |
| `trimStart` | `Boolean` | `true` | | Remove leading silence |
| `trimEnd` | `Boolean` | `true` | | Remove trailing silence |

## Behaviour

- A run of samples is considered silent when their RMS/peak level stays below `thresholdDb` for at least `minSilenceMs` milliseconds.
- Only silence at the absolute start and/or end is trimmed; internal silence is preserved.

## DSL usage

```kotlin
val transmuter = Transmute.audio.to(AudioFormat.Mp3) {
    decode {
        pipeline {
            silenceTrim(thresholdDb = -50f, minSilenceMs = 200L)
        }
    }
}

// Only trim the end
val transmuter = Transmute.audio {
    decode {
        pipeline { silenceTrim(trimStart = false) }
    }
}
```

## Related

- [trim](trim.md)
- [fade](fade.md)
- [Transforms overview](README.md)
