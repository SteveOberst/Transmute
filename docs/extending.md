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
    override val key = pluginId("com.example.my-codec")
    override fun createConfig() = MyCodecConfig()

    override fun install(scope: TransmuteScope, config: MyCodecConfig) {
        scope.codecs.image.decoders.register(MyWebpDecoder())
        scope.codecs.image.encoders.register(MyWebpEncoder())
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

// Register via the static TransmuteStructure API (app-level, outside a plugin):
Transmute.structure.register(MyTiffStructureReader(), ImageFormat.Tiff)
```

Custom readers override the built-in reader when registered for the same format.

### Structure decoders inside a plugin: `rawDecoderFor` / `structureDecoderFor`

When registering structure readers inside a `TransmutePlugin`, use the factory
functions `rawDecoderFor` and `structureDecoderFor` from `transmute-structure`
instead of creating named subclasses:

```kotlin
import dev.transmute.structure.rawDecoderFor
import dev.transmute.structure.structureDecoderFor

object MyPlugin : SimpleTransmutePlugin() {
    override val key = pluginId("com.example.my-plugin")

    override fun install(scope: TransmuteScope) {
        // Raw decoder: bytes → RawMediaStructure (no toStructure() step)
        val myRawDecoder = rawDecoderFor(ImageFormat.Tiff, MyTiffStructureReader())
        scope.codecs.image.rawStructureDecoders.register(ImageFormat.Tiff, myRawDecoder)

        // Full structure decoder: bytes → MediaStructure (applies toStructure())
        val myDecoder = structureDecoderFor(ImageFormat.Tiff, MyTiffStructureReader()) {
            toStructure() // extension on MyTiffStructure
        }
        scope.codecs.image.structureDecoders.register(ImageFormat.Tiff, myDecoder)
    }
}
```

Both factory functions create anonymous `Decoder<F, OUT, NoDecodeOptions>` instances
that delegate `sniff` and `decode` to the underlying `StructureReader` — no boilerplate
subclasses needed.

The pre-built reader singletons in `DefaultStructureReaders` can be used directly:

```kotlin
import dev.transmute.structure.DefaultStructureReaders
import dev.transmute.structure.rawDecoderFor

// Reuse the built-in WAV reader instead of instantiating your own:
val wavRaw = rawDecoderFor(AudioFormat.Wav, DefaultStructureReaders.wav)
scope.codecs.audio.rawStructureDecoders.register(AudioFormat.Wav, wavRaw)
```

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

