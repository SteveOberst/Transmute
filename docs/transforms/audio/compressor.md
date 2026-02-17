# Compressor

Dynamic range compressor - reduces the volume of loud passages.

## Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| thresholdDb | Float | -20f | Level above which compression is applied (dB) |
| ratio | Float | 4f | Compression ratio (e.g., 4:1) |
| attackMs | Float | 5f | Attack time in milliseconds |
| releaseMs | Float | 50f | Release time in milliseconds |
| makeupGainDb | Float | 0f | Post-compression gain boost in dB |

## Usage

### DSL

```kotlin
Transmute.audio(bytes) { compressor(thresholdDb = -20f, ratio = 4f) }
```

### Pipeline

```kotlin
transform { add(Transformers.audio().compressor(-20f, 4f)) }
```

## Notes

- Operates on a sample-by-sample basis with envelope following.
- `ratio = 1` means no compression; higher ratios produce heavier compression.
- Use `makeupGainDb` to compensate for the overall volume reduction.
