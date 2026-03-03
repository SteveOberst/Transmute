# Video: trim

Trim to a specific time range.

## Factory

```kotlin
Transformers.video().trim(startMs: Long, endMs: Long? = null)
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `startMs` | `Long` | ✅ | Start time in milliseconds (≥ 0) |
| `endMs` | `Long?` | | End time in milliseconds; `null` trims to the end of the video |

## Behaviour

- Frames and audio samples outside [startMs, endMs] are discarded.
- Timestamps are rebased to start from 0 in the output.
- If `startMs` exceeds the video duration the result is empty.

## DSL usage

```kotlin
// Keep seconds 5–30
val transmuter = Transmute.video.to(VideoFormat.Mp4) {
    decode {
        pipeline { trim(startMs = 5_000L, endMs = 30_000L) }
    }
}

// Keep from 10 s to end
val transmuter = Transmute.video.to(VideoFormat.Mp4) {
    decode {
        pipeline { trim(startMs = 10_000L) }
    }
}
```

## Related

- [speed](speed.md)
- [removeAudio](remove-audio.md)
- [Transforms overview](README.md)
