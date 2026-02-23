# Crop (Video)

Crop video frames to a rectangular sub-region.

## Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| x | Int | - | Left edge of crop region |
| y | Int | - | Top edge of crop region |
| width | Int | - | Width of crop region |
| height | Int | - | Height of crop region |

## Usage

### DSL

```kotlin
Transmute.video { crop(x = 0, y = 0, width = 640, height = 480) }.transmute(bytes.asBytes()).bytes.data
```

### Pipeline

```kotlin
transform { add(Transformers.video().crop(0, 0, 640, 480)) }
```

## Notes

- Applied to every frame in the video.
- Dimensions are rounded to even numbers for codec compatibility.
- Coordinates exceeding the frame size are clamped.
