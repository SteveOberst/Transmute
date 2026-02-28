# Rotate

Rotate an image by an explicit number of degrees clockwise.

Supported angles: 90°, 180°, 270°. Rotation is a pure pixel shuffle — no interpolation, no quality loss. The orientation field is reset to `NORMAL` in the output.

## Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| degrees | Int | 90 | Rotation in degrees clockwise: 90, 180, or 270 |

## Usage

### DSL

```kotlin
// 90° clockwise (default)
Transmute.image { rotate() }.transmute(bytes.asBytes()).bytes.data

// 180° (upside-down)
Transmute.image { rotate(180) }.transmute(bytes.asBytes()).bytes.data
```

### Pipeline

```kotlin
transform { add(Transformers.image().rotate()) }      // 90° CW (default)
transform { add(Transformers.image().rotate(180)) }   // 180°
```

## Notes

- Valid values: 90, 180, 270. Any other value throws `IllegalArgumentException`.
- 90° and 270° swap the image width and height; 180° preserves dimensions.
- The `orientation` field of the output IR is always set to `NORMAL`.
