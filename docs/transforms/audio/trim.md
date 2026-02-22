# Trim

Trim audio to a specific time range.

## Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| startMs | Long | - | Start time in milliseconds |
| endMs | Long? | null | End time in milliseconds; `null` means end of audio |

## Usage

### DSL

```kotlin
import dev.transmute.core.asBytes
Transmute.audio { trim(startMs = 1000, endMs = 5000) }.transmute(bytes.asBytes()).bytes.data
```

### Pipeline

```kotlin
transform { add(Transformers.audio().trim(1000, 5000)) }
```

## Notes

- `endMs = null` trims from `startMs` to the end of the file.
- Values exceeding the audio duration are clamped.
- Sample-accurate - the nearest sample boundary is used.
