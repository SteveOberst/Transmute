# Audio: fade

Apply fade-in and/or fade-out envelopes.

## Factory

```kotlin
Transformers.audio().fade(fadeInMs: Long = 0, fadeOutMs: Long = 0)
```

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `fadeInMs` | `Long` | `0` | Fade-in duration in milliseconds (0 = no fade in) |
| `fadeOutMs` | `Long` | `0` | Fade-out duration in milliseconds (0 = no fade out) |

## Behaviour

- Applies a linear amplitude envelope:
  - **Fade in:** samples ramp from 0 → full during the first `fadeInMs` milliseconds.
  - **Fade out:** samples ramp from full → 0 during the last `fadeOutMs` milliseconds.
- Both may be specified simultaneously; if their ranges overlap they are compounded.

## DSL usage

```kotlin
val transmuter = Transmute.audio.to(AudioFormat.Mp3) {
    decode {
        pipeline {
            fade(fadeInMs = 500, fadeOutMs = 1000)
        }
    }
}
```

## Related

- [trim](trim.md)
- [silenceTrim](silence-trim.md)
- [Transforms overview](README.md)
