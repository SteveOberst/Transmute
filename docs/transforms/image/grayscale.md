# Grayscale

Convert an image to grayscale using perceptual luminance coefficients.

## Parameters

None.

## Usage

### DSL

```kotlin
Transmute.image(bytes) { grayscale() }
```

### Pipeline

```kotlin
transform { add(Transformers.image().grayscale()) }
```

## Notes

- Uses BT.709 luma coefficients: `0.2126R + 0.7152G + 0.0722B`.
- Alpha channel is preserved unchanged.
