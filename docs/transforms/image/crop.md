# Crop

Crop an image to a rectangular sub-region.

## Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| x | Int | — | Left edge of crop region |
| y | Int | — | Top edge of crop region |
| width | Int | — | Width of crop region |
| height | Int | — | Height of crop region |

## Usage

### DSL

```kotlin
Transmute.image(bytes) { crop(x = 100, y = 50, width = 400, height = 300) }
```

### Pipeline

```kotlin
transform { add(Transformers.image().crop(100, 50, 400, 300)) }
```

## Notes

- Coordinates are clamped to image bounds — out-of-range values are adjusted automatically.
- Operates on the pixel grid of the intermediate representation.
