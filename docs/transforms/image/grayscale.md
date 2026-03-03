# Image: grayscale

Convert an image to grayscale using BT.709 luma coefficients.

## Factory

```kotlin
Transformers.image().grayscale()
```

No parameters.

## Behaviour

- Uses the BT.709 luma formula: `Y = 0.2126R + 0.7152G + 0.0722B`.
- The output image retains the original channel structure (RGB / RGBA); all three colour channels are set to the computed luma value. The alpha channel is unchanged.

## DSL usage

```kotlin
val transmuter = Transmute.image {
    decode {
        pipeline { grayscale() }
    }
}
```

## Related

- [brightnessContrast](brightness-contrast.md)
- [opacity](opacity.md)
- [Transforms overview](README.md)
