# Scale

Fit an image within maximum bounds while preserving aspect ratio. Never upscales.

## Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| maxWidth | Int | - | Maximum output width in pixels |
| maxHeight | Int | - | Maximum output height in pixels |

## Usage

### DSL

```kotlin
Transmute.image { scale(maxWidth = 1920, maxHeight = 1080) }.transmute(bytes).bytes
```

### Pipeline

```kotlin
transform { add(Transformers.image().scale(1920, 1080)) }
```

## Notes

- Aspect ratio is always preserved; the image fits within the bounding box.
- If the image is already smaller than the bounds, it is returned unchanged.
- Implemented by `ImageScaleTransform`.
