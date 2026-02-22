# Silence Trim

Trim silence from the start and/or end of audio.

## Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| thresholdDb | Float | -40f | Silence threshold in dB below peak |
| minSilenceMs | Long | 100 | Minimum consecutive silence duration to detect |
| trimStart | Boolean | true | Trim silence from the beginning |
| trimEnd | Boolean | true | Trim silence from the end |

## Usage

### DSL

```kotlin
Transmute.audio { silenceTrim(thresholdDb = -40f) }.transmute(bytes).bytes
```

### Pipeline

```kotlin
transform { add(Transformers.audio().silenceTrim(-40f)) }
```

## Notes

- Scans samples against the dB threshold to locate leading/trailing silence.
- `minSilenceMs` prevents trimming brief pauses shorter than the minimum.
- Set `trimStart` or `trimEnd` to `false` to trim only one end.
