# Gain

Apply a volume gain in decibels.

## Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| db | Float | - | Gain in dB; positive = louder, negative = quieter |

## Usage

### DSL

```kotlin
Transmute.audio { gain(db = 3f) }.transmute(bytes).bytes
```

### Pipeline

```kotlin
transform { add(Transformers.audio().gain(3f)) }
```

## Notes

- Converts dB to a linear multiplier: `10^(db / 20)`.
- +6 dB ≈ double amplitude, −6 dB ≈ half amplitude.
- Samples that exceed the valid range after gain are clipped.
