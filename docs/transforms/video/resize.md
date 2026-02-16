# Resize (Video)

Fit video frames within maximum bounds while preserving aspect ratio.

## Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| maxWidth | Int | — | Maximum output width in pixels |
| maxHeight | Int | — | Maximum output height in pixels |

## Usage

### DSL

```kotlin
Transmute.video(bytes) { resize(maxWidth = 1280, maxHeight = 720) }
```

### Pipeline

```kotlin
transform { add(Transformers.video().resize(1280, 720)) }
```

## Notes

- Aspect ratio is always preserved; the video fits within the bounding box.
- Dimensions are rounded to even numbers (required by most video codecs).
- Does not upscale — if the video is already smaller, it is returned unchanged.
