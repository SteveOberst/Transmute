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

### Via the Plugin System

For reusable codec bundles, wrap registration in a `TransmutePlugin`:

```kotlin
object MyCodecPlugin : TransmutePlugin<MyCodecConfig> {
    override val key = "com.example.my-codec"
    override fun createConfig() = MyCodecConfig()

    override fun install(scope: TransmuteScope, config: MyCodecConfig) {
        scope.imageDecoders.register(MyWebpDecoder())
        scope.imageEncoders.register(MyWebpEncoder())
    }
}

val transmute = Transmute {
    plugins { install(MyCodecPlugin) }
}
```

See [plugins.md](plugins.md) for the full plugin system documentation.

## Custom transforms

Transforms are just `Transform<IR>` implementations. You can add them directly in a transmuter:

```kotlin
class WatermarkTransform : dev.transmute.codec.pipeline.Transform<ImageIR> {
    override val id = dev.transmute.codec.pipeline.TransformId("image.watermark")
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

Custom readers override the built-in reader when registered for the same format.

### Reading from a TSource

Once registered, your reader works automatically with all `TransmuteStructure` overloads,
including the suspending `TSource` variants:

```kotlin
suspend fun readTiff(source: TSource): MyTiffStructure =
    Transmute.structure.read(source, ImageFormat.Tiff)

// Lambda sugar
suspend fun tiffWidth(source: TSource): Int =
    Transmute.structure.read<MyTiffStructure>(source, ImageFormat.Tiff) { width }
```

See [structures.md](structures.md) for the full structure API, including
IO abstractions (`TSource`, `TSink`, `TChannel`).

## Module reference

| Module                | README                                     | Key types                                    |
|-----------------------|--------------------------------------------|----------------------------------------------|
| `transmute-codec`     | [README](../transmute-codec/README.md)     | Decoder, Encoder, Pipeline                   |
| `transmute-structure` | [README](../transmute-structure/README.md) | StructureReader, all 20 readers              |
| `transmute-api`       | [README](../transmute-api/README.md)       | TransmuteStructure, TSource, TSink, TChannel |

