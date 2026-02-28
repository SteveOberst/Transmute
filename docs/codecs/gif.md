# GIF

GIF (Graphics Interchange Format) is a legacy image format best known for simple animations. It has limited color depth and is generally not ideal for modern still images.

## Platform Support

| Platform | Decode | Encode | Engine                      |
|----------|--------|--------|-----------------------------|
| Android  | ✅      | ❌      | BitmapFactory (decode only) |
| Desktop  | ✅      | ✅      | ImageIO (javax.imageio)     |
| iOS      | ✅      | ✅      | CoreGraphics (CGImage)      |

## Usage

```kotlin
// Convert any image to GIF (Desktop/iOS)
suspend fun convertToGif(inputBytes: ByteArray): ByteArray =
  Transmute.image {
    encode { options { outputFormat = OutputFormat.Exact(ImageFormat.Gif) } }
  }.transmute(inputBytes.asBytes()).bytes.data

// Decode GIF (re-encode to PNG)
suspend fun decodeToPng(gifBytes: ByteArray): ByteArray =
  Transmute.image {
    encode { options(PngEncodeOptions()) }
  }.transmute(gifBytes.asBytes()).bytes.data
```

## Notes

- Limited to 256 colors; expect banding/dithering for photos.
- Primarily used for simple animations and stickers.
