# Audio: trim

Trim to a specific time range.

## Factory

```kotlin
Transformers.audio().trim(startMs: Long, endMs: Long? = null)
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `startMs` | `Long` | ✅ | Start time in milliseconds (≥ 0) |
| `endMs` | `Long?` | | End time in milliseconds; `null` trims to the end of the audio |

## Behaviour

- Samples before `startMs` are discarded.
- Samples after `endMs` are discarded (if `endMs` is not `null`).
- If `startMs` exceeds the audio duration the result is silence.

## DSL usage

```kotlin
// Keep seconds 10–40
val transmuter = Transmute.audio.to(AudioFormat.Mp3) {
    decode {
        pipeline { trim(startMs = 10_000L, endMs = 40_000L) }
    }
}

// Keep from 5 s to end
val transmuter = Transmute.audio {
    decode {
        pipeline { trim(startMs = 5_000L) }
    }
}
```

## Related

- [fade](fade.md)
- [silenceTrim](silence-trim.md)
- [Transforms overview](README.md)
