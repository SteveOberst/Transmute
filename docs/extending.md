# Extending Transmute (Custom Codecs, Transforms & Structure Readers)

## Custom codecs

You can register custom codecs (or split decoders/encoders) in the registries.

Example: register a custom image decoder + encoder:

```kotlin
class MyWebpDecoder : ImageDecoder {
  override val supportedFormats = setOf(ImageFormat.Webp)
  override fun sniff(data: Bytes): ImageFormat? = null // optional magic bytes
  override suspend fun decode(source: Bytes, options: ImageDecodeOptions, context: TransmuteContext): ImageIR = TODO()
}

class MyWebpEncoder : ImageEncoder {
  override val supportedFormats = setOf(ImageFormat.Webp)
  override suspend fun encode(
    ir: ImageIR,
    format: ImageFormat,
    options: ImageEncodeOptions,
    context: TransmuteContext,
  ): Bytes = TODO()
}

ImageRegistries.register(MyWebpDecoder())
ImageRegistries.register(MyWebpEncoder())
```

You can also register unified codecs, or register core `Decoder` / `Encoder` instances directly; see `transmute-*/.../ImageRegistry.kt` / `AudioRegistry.kt` / `VideoRegistry.kt`.

## Custom transforms

Transforms are just `Transform<IR>` implementations. You can add them directly in a transmuter:

```kotlin
class WatermarkTransform : dev.transmute.codec.pipeline.Transform<ImageIR> {
    override val id = dev.transmute.core.pipeline.TransformId("image.watermark")
    override suspend fun apply(ir: ImageIR, ctx: TransmuteContext): ImageIR = TODO()
}

val t =
    Transmute.image {
        transform { add(WatermarkTransform()) }
    }
```

## Custom structure readers

A `StructureReader<S>` parses raw bytes into a `MediaStructure` subtype without decoding pixel/sample data.

```kotlin
class MyTiffStructureReader : StructureReader<MyTiffStructure> {
    override fun canRead(source: Bytes): Boolean {
        if (source.data.size < 4) return false
        val h = source.data
        return (h[0] == 0x49.toByte() && h[1] == 0x49.toByte()) ||
               (h[0] == 0x4D.toByte() && h[1] == 0x4D.toByte())
    }

    override fun read(source: Bytes): MyTiffStructure {
        // Parse the TIFF binary layout into your data class
        TODO()
    }
}

// Register for a specific format
Transmute.structure.register(MyTiffStructureReader(), ImageFormat.Tiff)
```

Custom readers override the built-in reader when registered for the same format. See `docs/structures.md` for the full structure API.

