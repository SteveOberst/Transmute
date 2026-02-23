# Trim (Video)

Trim a video to a specific time range.

## Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| startMs | Long | - | Start time in milliseconds |
| endMs | Long? | null | End time in milliseconds; `null` means end of video |

## Usage

### DSL

```kotlin
Transmute.video { trim(startMs = 0, endMs = 30_000) }.transmute(bytes.asBytes()).bytes.data
```

### Pipeline

```kotlin
transform { add(Transformers.video().trim(0, 30_000)) }
```

## Notes

- Trims both video and audio tracks to the specified range.
- Seeks to the nearest keyframe for the start point to avoid decoding artifacts.
- Values exceeding the video duration are clamped.
