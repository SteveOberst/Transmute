# Flip

Mirror an image horizontally and/or vertically.

## Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| horizontal | Boolean | false | Mirror along the vertical axis (left ↔ right) |
| vertical | Boolean | false | Mirror along the horizontal axis (top ↔ bottom) |

## Usage

### DSL

```kotlin
Transmute.image(bytes) { flip(horizontal = true) }
```

### Pipeline

```kotlin
transform { add(Transformers.image().flip(horizontal = true, vertical = false)) }
```

## Notes

- At least one of `horizontal` or `vertical` should be `true`; otherwise the image is unchanged.
- Both can be `true` simultaneously (equivalent to a 180° rotation).
