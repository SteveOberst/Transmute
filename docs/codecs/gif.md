# GIF

GIF (Graphics Interchange Format) is a legacy image format best known for simple animations. It has limited color depth and is generally not ideal for modern still images.

## Platform Support

| Platform | Decode | Encode | Engine |
|----------|--------|--------|--------|
| Android  | ✅     | ❌     | BitmapFactory (decode only) |
| Desktop  | ✅     | ✅     | ImageIO (javax.imageio) |
| iOS      | ✅     | ✅     | CoreGraphics (CGImage) |

## Usage

```kotlin
import dev.transmute.Transmute
import dev.transmute.core.ImageFormat
import dev.transmute.image.DefaultImageEncodeOptions
import dev.transmute.image.PngEncodeOptions

// Convert any image to GIF (Desktop/iOS)
suspend fun convertToGif(inputBytes: ByteArray): ByteArray =
  Transmute.image {
    encodeOptions(DefaultImageEncodeOptions(outputFormat = ImageFormat.GIF))
  }.transmute(inputBytes).bytes

// Decode GIF (re-encode to PNG)
suspend fun decodeToPng(gifBytes: ByteArray): ByteArray =
  Transmute.image {
    encodeOptions(PngEncodeOptions())
  }.transmute(gifBytes).bytes
```

## Notes

- Limited to 256 colors; expect banding/dithering for photos.
- Primarily used for simple animations and stickers.
