# Opacity

Adjust the alpha channel of every pixel.

## Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| opacity | Float | - | Opacity multiplier, 0.0 = fully transparent, 1.0 = unchanged |

## Usage

### DSL

```kotlin
import dev.transmute.core.asBytes
Transmute.image { opacity(0.5f) }.transmute(bytes.asBytes()).bytes.data
```

### Pipeline

```kotlin
transform { add(Transformers.image().opacity(0.5f)) }
```

## Notes

- Each pixel's alpha is multiplied by `opacity`.
- RGB channels are not modified.
- `opacity = 1.0` is a no-op.
