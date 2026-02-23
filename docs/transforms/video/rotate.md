# Rotate (Video)

Rotate video frames by a fixed angle clockwise.

## Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| degrees | Int | - | Rotation angle: 90, 180, or 270 |

## Usage

### DSL

```kotlin
Transmute.video { rotate(degrees = 90) }.transmute(bytes.asBytes()).bytes.data
```

### Pipeline

```kotlin
transform { add(Transformers.video().rotate(90)) }
```

## Notes

- Only 90°, 180°, and 270° are supported.
- 90° and 270° swap the width and height of each frame.
- Applied to every frame; audio is unaffected.
