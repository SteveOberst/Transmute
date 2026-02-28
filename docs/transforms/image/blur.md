# Blur

Apply a box blur to an image.

## Parameters

| Parameter | Type | Default | Description                                |
|-----------|------|---------|--------------------------------------------|
| radius    | Int  | -       | Blur radius; 1 = 3×3 kernel, 2 = 5×5, etc. |

## Usage

### DSL

```kotlin
Transmute.image { blur(radius = 2) }.transmute(bytes.asBytes()).bytes.data
```

### Pipeline

```kotlin
transform { add(Transformers.image().blur(2)) }
```

## Notes

- Kernel size is `(2 * radius + 1) × (2 * radius + 1)`.
- Uses uniform (box) averaging; all kernel weights are equal.
- Edge pixels are clamped (no wrap-around).
