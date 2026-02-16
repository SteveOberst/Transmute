# Brightness / Contrast

Adjust image brightness and contrast.

## Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| brightness | Float | 0f | Brightness offset, range -255..+255 |
| contrast | Float | 1f | Contrast multiplier, range 0..3 |

## Usage

### DSL

```kotlin
Transmute.image(bytes) { brightnessContrast(brightness = 10f, contrast = 1.2f) }
```

### Pipeline

```kotlin
transform { add(Transformers.image().brightnessContrast(10f, 1.2f)) }
```

## Notes

- `brightness = 0, contrast = 1` is a no-op.
- Contrast is applied as a linear scale around the midpoint (128).
- Values are clamped to the valid pixel range after transformation.
