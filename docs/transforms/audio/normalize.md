# Normalize

Peak amplitude normalization — scales the entire signal so the loudest sample reaches a target peak.

## Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| targetPeak | Float | 0.95f | Target peak amplitude, 0.0–1.0 |

## Usage

### DSL

```kotlin
Transmute.audio(bytes) { normalize(targetPeak = 0.9f) }
```

### Pipeline

```kotlin
transform { add(Transformers.audio().normalize(0.9f)) }
```

## Notes

- Scans all samples to find the current peak, then applies a uniform gain.
- No-op if the audio is already at or above the target peak.
- Applied per-channel but with the same gain factor for all channels to preserve stereo balance.
