# Fade

Apply fade-in and/or fade-out amplitude envelopes.

## Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| fadeInMs | Long | 0 | Fade-in duration in milliseconds |
| fadeOutMs | Long | 0 | Fade-out duration in milliseconds |

## Usage

### DSL

```kotlin
Transmute.audio { fade(fadeInMs = 100, fadeOutMs = 200) }.transmute(bytes).bytes
```

### Pipeline

```kotlin
transform { add(Transformers.audio().fade(100, 200)) }
```

## Notes

- Uses a linear amplitude ramp for both fade-in and fade-out.
- If `fadeInMs + fadeOutMs` exceeds the audio duration, the envelopes overlap in the middle.
- Set either value to `0` to skip that end.
