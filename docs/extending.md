# Extending Transmute

Transmute is designed to be extended through the plugin system. Plugins can register custom decoders, encoders, transforms, structure decoders, and metadata decoders.

## Creating a plugin

### Simple plugin (no configuration)

```kotlin
object MyImagePlugin : SimpleTransmutePlugin() {
    override val key = pluginId("com.example.my-image-plugin")

    override fun install(scope: TransmuteScope) {
        scope.codecs.image.decoders.register(MyImageDecoder())
        scope.codecs.image.encoders.register(MyImageEncoder())
    }
}

// Install it:
val transmute = transmute {
    plugins { install(MyImagePlugin) }
}
```

### Configurable plugin

```kotlin
data class MyPluginConfig(var quality: Float = 0.8f, var enableHardware: Boolean = false)

class MyPlugin : TransmutePlugin<MyPluginConfig> {
    override val key = pluginId("com.example.my-plugin")

    override fun createConfig() = MyPluginConfig()

    override fun install(scope: TransmuteScope, config: MyPluginConfig) {
        scope.codecs.image.encoders.register(MyEncoder(config.quality, config.enableHardware))
    }
}

// Install with configuration:
val transmute = transmute {
    plugins {
        install(MyPlugin()) {
            quality = 0.95f
            enableHardware = true
        }
    }
}
```

## Writing a custom decoder

Implement `MediaDecoder<F, OUT, D>` where `F` is the format type, `OUT` is the intermediate representation, and `D` is the decode options type:

```kotlin
class MyJpegDecoder : MediaDecoder<ImageFormat, Decoded<ImageFormat, ImageIR>, ImageDecodeOptions> {

    override val decodableFormats: Set<ImageFormat> = setOf(ImageFormat.Jpeg)

    override suspend fun decode(
        source: TSource,
        options: ImageDecodeOptions,
        context: PipelineContext,
    ): Decoded<ImageFormat, ImageIR> {
        val bytes = source.readAll()
        // ... decode bytes into ImageIR ...
        val ir = ImageIR(
            buffer = pixelData,
            width = width,
            height = height,
            stride = width * 4,
            pixelFormat = PixelFormat.ARGB_8888,
            alphaSemantics = AlphaSemantics.PREMULTIPLIED,
            colorInfo = ColorInfo(),
        )
        return Decoded(ImageFormat.Jpeg, ir)
    }
}
```

Register it in your plugin:

```kotlin
override fun install(scope: TransmuteScope) {
    scope.codecs.image.decoders.register(MyJpegDecoder())
}
```

## Writing a custom encoder

Implement `MediaEncoder<F, IN, O>`:

```kotlin
class MyWebpEncoder : MediaEncoder<ImageFormat, Decoded<ImageFormat, ImageIR>, ImageEncodeOptions> {

    override val encodableFormats: Set<ImageFormat> = setOf(ImageFormat.Webp)

    override suspend fun encode(
        ir: Decoded<ImageFormat, ImageIR>,
        format: ImageFormat,
        options: ImageEncodeOptions,
        context: PipelineContext,
    ): Bytes {
        // ... encode ir.ir (the ImageIR) to WebP bytes ...
        return encodedBytes.asBytes()
    }
}
```

## Writing a custom transform

Implement `Transform<IR>`:

```kotlin
class ImageSharpnessTransform(private val amount: Float) : Transform<ImageIR> {
    override val id: TransformId = TransformId("com.example.sharpness")

    override suspend fun apply(ir: ImageIR, context: PipelineContext): ImageIR {
        // apply sharpening to ir.buffer
        return sharpened
    }
}

// Use it:
Transmute.image {
    transform { add(ImageSharpnessTransform(1.5f)) }
}.transmute(source)
```

## Writing a custom structure decoder

Structure decoders return `MediaStructure` (high-level) or `RawMediaStructure` (binary-level):

```kotlin
// Implement a MediaDecoder that produces MediaStructure
class MyFormatStructureDecoder : MediaDecoder<ImageFormat, MediaStructure, NoDecodeOptions> {
    override val decodableFormats: Set<ImageFormat> = emptySet() // unused by structure registry

    override suspend fun decode(
        source: TSource,
        options: NoDecodeOptions,
        context: PipelineContext,
    ): MediaStructure {
        val bytes = source.readAll().asBytes()
        // parse bytes into MyFormatStructure (which implements MediaStructure)
        return MyFormatStructure(/* ... */)
    }
}

// Register in your plugin alongside a serializer:
override fun install(scope: TransmuteScope) {
    scope.codecs.image.structureDecoders.register(ImageFormat.Heic, MyFormatStructureDecoder())
    scope.mediaStructures.register("com.example.heic", HeicStructure.serializer())
}
```

## Writing a custom metadata decoder

```kotlin
class MyAacMetadataDecoder : MediaDecoder<AudioFormat, List<MediaMetadata>, NoDecodeOptions> {
    override val decodableFormats: Set<AudioFormat> = emptySet()

    override suspend fun decode(
        source: TSource,
        options: NoDecodeOptions,
        context: PipelineContext,
    ): List<MediaMetadata> {
        val bytes = source.readAll().asBytes()
        return listOf(parseId3v2(bytes))
    }
}

override fun install(scope: TransmuteScope) {
    scope.codecs.audio.metadataDecoders.register(AudioFormat.Aac, MyAacMetadataDecoder())
    scope.mediaMetadata.register("com.example.mymeta", MyMetadata.serializer())
}
```

## Registries available in TransmuteScope

| Registry | Type |
|----------|------|
| `scope.codecs.image.decoders` | `MutableImageDecoderRegistry` |
| `scope.codecs.image.encoders` | `MutableImageEncoderRegistry` |
| `scope.codecs.image.structureDecoders` | Format→Structure registry |
| `scope.codecs.image.rawStructureDecoders` | Format→RawStructure registry |
| `scope.codecs.image.metadataDecoders` | Format→List\<MediaMetadata\> registry |
| `scope.codecs.audio.*` | Same for audio |
| `scope.codecs.video.*` | Same for video |
| `scope.services` | `ServiceRegistry` (cross-plugin) |
| `scope.mediaStructures` | `MediaStructureRegistrationScope` |
| `scope.mediaMetadata` | `MediaMetadataRegistrationScope` |
