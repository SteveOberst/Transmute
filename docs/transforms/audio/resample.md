# Resample

Change the sample rate of audio data.

## Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| targetSampleRate | Int | - | Desired output sample rate in Hz |

## Usage

### DSL

```kotlin
Transmute.audio { resample(targetSampleRate = 22050) }.transmute(bytes.asBytes()).bytes.data
```

### Pipeline

```kotlin
transform { add(Transformers.audio().resample(22050)) }
```

## Notes

- Uses linear interpolation for resampling.
- Both upsampling and downsampling are supported.
- No-op if the source sample rate already matches the target.
