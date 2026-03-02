package dev.transmute.playground

import dev.transmute.Transmute
import dev.transmute.transmute
import dev.transmute.gstreamer.GStreamer
import dev.transmute.playground.shared.*
import dev.transmute.plugin.PluginId
import dev.transmute.image.ImageFormat
import dev.transmute.image.ImageTransform
import dev.transmute.audio.AudioFormat
import dev.transmute.audio.AudioTransform
import dev.transmute.video.VideoFormat
import dev.transmute.video.VideoTransform
import dev.transmute.model.core.MediaFormat
import dev.transmute.model.core.asBytes
import dev.transmute.AudioTransforms
import dev.transmute.ImageTransforms
import dev.transmute.Param
import dev.transmute.TransformDescriptor
import dev.transmute.VideoTransforms
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberFunctions

/**
 * Owns the Transmute instance and manages uploaded files.
 *
 * All media processing operations flow through this service.
 * Plugin and format information is derived **dynamically** from the
 * Transmute instance's codec registries - nothing is hardcoded.
 */
class TransmuteService(
    private val tempDir: File = File(System.getProperty("java.io.tmpdir"), "transmute-playground"),
    initiallyDisabledPlugins: Set<PluginId> = emptySet(),
) {
    private val log = LoggerFactory.getLogger(TransmuteService::class.java)
    private val files = ConcurrentHashMap<String, UploadedFile>()

    // -- Plugin management (dynamic) -------------------------------------------

    private val featureOverrides = mutableMapOf<String, Boolean>()
    private val disabledPlugins = mutableSetOf<PluginId>().apply { addAll(initiallyDisabledPlugins) }

    /** All plugins that this playground knows about, keyed by plugin ID string. */
    private val knownPlugins: Map<String, PluginId> = mapOf(
        GStreamer.key.id to GStreamer.key,
    )

    /** Human-readable display name for known plugins. */
    private val pluginDisplayNames: Map<String, String> = mapOf(
        GStreamer.key.id to "GStreamer",
    )

    /** Short description for known plugins. */
    private val pluginDescriptions: Map<String, String> = mapOf(
        GStreamer.key.id to "GStreamer-based codec backend — adds video, audio and container support via libgstreamer.",
    )

    /**
     * Format labels available without any plugins installed.
     * Used to distinguish platform-native formats from plugin-provided ones.
     */
    private val builtInFormatLabels: Set<String> by lazy {
        val base = transmute { }
        try {
            buildSet {
                (base.codec.image.decodableFormats + base.codec.image.encodableFormats)
                    .filterNot { it is ImageFormat.Unknown }.forEach { add(it.label) }
                (base.codec.audio.decodableFormats + base.codec.audio.encodableFormats)
                    .filterNot { it is AudioFormat.Unknown }.forEach { add(it.label) }
                (base.codec.video.decodableFormats + base.codec.video.encodableFormats)
                    .filterNot { it is VideoFormat.Unknown }.forEach { add(it.label) }
            }
        } finally {
            base.close()
        }
    }

    @Volatile
    var transmute: Transmute = buildTransmute()
        private set

    init {
        tempDir.mkdirs()
    }

    // -- File management --------------------------------------------------------

    fun storeFile(name: String, bytes: ByteArray): FileHandle {
        val handle = UUID.randomUUID().toString()
        val file = File(tempDir, handle)
        file.writeBytes(bytes)

        val uploaded = UploadedFile(handle, name, bytes.size.toLong(), file)
        files[handle] = uploaded

        return FileHandle(
            handle = handle,
            originalName = name,
            fileSize = bytes.size.toLong(),
        )
    }

    fun getFile(handle: String): UploadedFile? = files[handle]

    fun getFileBytes(handle: String): ByteArray? {
        val uploaded = files[handle] ?: return null
        return uploaded.file.readBytes()
    }

    fun listFiles(): List<UploadedFile> = files.values.toList()

    // -- Inspect ----------------------------------------------------------------

    suspend fun inspect(handle: String): InspectResult? {
        val uploaded = files[handle] ?: return null
        val bytes = uploaded.file.readBytes()

        return try {
            val format = transmute.inspect.detectFormat(bytes)
            val domain = when (format) {
                is ImageFormat -> MediaDomainDto.IMAGE
                is AudioFormat -> MediaDomainDto.AUDIO
                is VideoFormat -> MediaDomainDto.VIDEO
                else -> MediaDomainDto.IMAGE
            }
            val structure = try {
                transmute.codec.decodeStructure(bytes.asBytes(), format)
            } catch (_: Exception) { null }

            InspectResult(
                domain = domain,
                format = format.label,
                fileSize = uploaded.size,
                structure = structure,
            )
        } catch (e: Exception) {
            log.error("Inspect failed for handle='$handle': ${e.message}", e)
            throw e
        }
    }

    // -- Dynamic format catalog ------------------------------------------------

    fun allFormats(): List<FormatInfo> = imageFormats() + audioFormats() + videoFormats()

    fun imageFormats() = formatList(MediaDomainDto.IMAGE, transmute.codec.image.decodableFormats, transmute.codec.image.encodableFormats) { it is ImageFormat.Unknown }
    fun audioFormats() = formatList(MediaDomainDto.AUDIO, transmute.codec.audio.decodableFormats, transmute.codec.audio.encodableFormats) { it is AudioFormat.Unknown }
    fun videoFormats() = formatList(MediaDomainDto.VIDEO, transmute.codec.video.decodableFormats, transmute.codec.video.encodableFormats) { it is VideoFormat.Unknown }

    private fun <F : MediaFormat<*, *>> formatList(
        domain: MediaDomainDto,
        decodable: Set<F>,
        encodable: Set<F>,
        isUnknown: (F) -> Boolean,
    ): List<FormatInfo> {
        val allKnown = (decodable + encodable).filterNot(isUnknown)
        return allKnown.map { fmt ->
            FormatInfo(
                name = fmt.label,
                domain = domain,
                canDecode = fmt in decodable,
                canEncode = fmt in encodable,
                hasStructureReader = transmute.codec.hasStructureDecoder(fmt),
                providedBy = if (fmt.label in builtInFormatLabels) null else GStreamer.key.id,
            )
        }.sortedBy { it.name }
    }

    // -- Reflection-driven transform catalog ---------------------------------
    //    Transform metadata is discovered at runtime from annotations on the
    //    Transformers.kt factory objects - nothing is hardcoded here.

    fun allTransforms(): List<TransformInfo> =
        imageTransforms() + audioTransforms() + videoTransforms()

    fun imageTransforms() = discoverTransforms(ImageTransforms, MediaDomainDto.IMAGE)
    fun audioTransforms() = discoverTransforms(AudioTransforms, MediaDomainDto.AUDIO)
    fun videoTransforms() = discoverTransforms(VideoTransforms, MediaDomainDto.VIDEO)

    private fun discoverTransforms(obj: Any, domain: MediaDomainDto): List<TransformInfo> {
        return obj::class.memberFunctions
            .mapNotNull { fn ->
                val desc = fn.findAnnotation<TransformDescriptor>() ?: return@mapNotNull null
                // Parameters: drop index 0 (instance receiver)
                val params = fn.parameters.drop(1).mapNotNull { param ->
                    val pa = param.findAnnotation<Param>() ?: return@mapNotNull null
                    val paramName = param.name ?: return@mapNotNull null
                    val classifier = param.type.classifier
                    // For nullable types (Long?, Float?, etc.) Kotlin reflection
                    // keeps the classifier set to the underlying class, so we use it directly.
                    val unwrapped = classifier
                    val (type, enumVals) = when {
                        unwrapped == Int::class || unwrapped == Long::class -> ParameterType.INT to null
                        unwrapped == Float::class || unwrapped == Double::class -> ParameterType.FLOAT to null
                        unwrapped == Boolean::class -> ParameterType.BOOLEAN to null
                        unwrapped == IntArray::class -> ParameterType.INT_ARRAY to null
                        unwrapped is KClass<*> && unwrapped.java.isEnum ->
                            ParameterType.ENUM to unwrapped.java.enumConstants.map { (it as Enum<*>).name }
                        else -> ParameterType.STRING to null
                    }
                    // Prefer explicit enumValues from @Param; fall back to reflection
                    val resolvedEnumValues = pa.enumValues
                        .split(',')
                        .filter { it.isNotEmpty() }
                        .ifEmpty { enumVals }
                    ParameterSchema(
                        name = paramName,
                        type = type,
                        required = pa.required,
                        default = pa.default.ifEmpty { null },
                        min = pa.min.ifEmpty { null },
                        max = pa.max.ifEmpty { null },
                        enumValues = resolvedEnumValues,
                        description = pa.description,
                    )
                }
                TransformInfo(
                    id = desc.id,
                    domain = domain,
                    description = desc.description,
                    parameters = params,
                )
            }
            .sortedBy { it.id }
    }

    fun listPlugins(): List<PluginDescriptor> {
        val installed = transmute.installedPlugins.associateBy { it.key.id }
        return knownPlugins.entries.map { (keyId, pluginId) ->
            val info = installed[keyId]
            PluginDescriptor(
                key = keyId,
                name = pluginDisplayNames[keyId] ?: keyId.substringAfterLast('.').replaceFirstChar { it.uppercase() },
                description = pluginDescriptions[keyId] ?: "Transmute plugin: $keyId",
                version = null,
                enabled = pluginId !in disabledPlugins,
                status = PluginStatusInfo(available = true),
                domains = deriveDomains(),
                features = info?.features?.map { feat ->
                    FeatureDescriptor(
                        id = feat.id,
                        name = feat.id.replace('-', ' ').split(' ')
                            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                        description = feat.description,
                        defaultEnabled = feat.defaultEnabled,
                        currentlyEnabled = featureOverrides[feat.id] ?: feat.defaultEnabled,
                    )
                } ?: emptyList(),
                addedFormats = deriveAddedFormats(),
            )
        }
    }

    fun getPlugin(key: String): PluginDescriptor? =
        listPlugins().firstOrNull { it.key == key }

    fun updatePlugin(key: String, update: PluginUpdate): PluginDescriptor? {
        val pluginId = knownPlugins[key] ?: return null

        update.enabled?.let { enabled ->
            if (enabled) disabledPlugins.remove(pluginId)
            else disabledPlugins.add(pluginId)
        }
        update.features?.forEach { (featureId, enabled) ->
            featureOverrides[featureId] = enabled
        }

        rebuildTransmute()
        return getPlugin(key)
    }

    private fun deriveDomains(): Set<MediaDomainDto> = buildSet {
        val img = transmute.codec.image
        if (img.decodableFormats.any { it !is ImageFormat.Unknown } ||
            img.encodableFormats.any { it !is ImageFormat.Unknown }) add(MediaDomainDto.IMAGE)
        val aud = transmute.codec.audio
        if (aud.decodableFormats.any { it !is AudioFormat.Unknown } ||
            aud.encodableFormats.any { it !is AudioFormat.Unknown }) add(MediaDomainDto.AUDIO)
        val vid = transmute.codec.video
        if (vid.decodableFormats.any { it !is VideoFormat.Unknown } ||
            vid.encodableFormats.any { it !is VideoFormat.Unknown }) add(MediaDomainDto.VIDEO)
    }

    private fun deriveAddedFormats(): List<String> = buildList {
        val codec = transmute.codec
        collectAddedLabels(codec.image.decodableFormats, codec.image.encodableFormats)
        collectAddedLabels(codec.audio.decodableFormats, codec.audio.encodableFormats)
        collectAddedLabels(codec.video.decodableFormats, codec.video.encodableFormats)
    }.distinct().sorted()

    private fun <F : MediaFormat<*, *>> MutableList<String>.collectAddedLabels(
        decodable: Set<F>,
        encodable: Set<F>,
    ) {
        (decodable + encodable)
            .filter { it.label.lowercase() != "unknown" && it.label !in builtInFormatLabels }
            .forEach { add(it.label) }
    }

    @Synchronized
    fun rebuildTransmute() {
        transmute.close()
        transmute = buildTransmute()
    }

    private fun humanReadableSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
        else -> "${"%.2f".format(bytes / (1024.0 * 1024))} MB"
    }

    private fun buildTransmute(): Transmute = transmute {
        plugins {
            if (GStreamer.key !in disabledPlugins) {
                install(GStreamer) {
                    featureOverrides.forEach { (id, enabled) -> set(id, enabled) }
                }
            }
        }
    }

    // -- Transform execution --------------------------------------------------

    /**
     * Executes a [TransformRequest] against a previously uploaded file using
     * the Transmute DSL.
     *
     * Transform instances are discovered at runtime via the same reflection-based
     * scanning used in [discoverTransforms]: factory functions on [ImageTransforms],
     * [AudioTransforms], and [VideoTransforms] are matched by [TransformDescriptor.id],
     * then invoked with parsed parameter values.
     */
    suspend fun executeTransform(request: TransformRequest): ByteArray {
        val inputFile = files[request.fileHandle] ?: error("File not found: ${request.fileHandle}")
        val inputBytes = inputFile.file.readBytes().asBytes()
        val fmt = request.outputFormat.lowercase().trim()
        require(fmt in IMAGE_FORMAT_EXTENSIONS || fmt in AUDIO_FORMAT_EXTENSIONS || fmt in VIDEO_FORMAT_EXTENSIONS) {
            "Unsupported output format: '$fmt'"
        }

        return when {
            fmt in IMAGE_FORMAT_EXTENSIONS -> executeImageTransform(inputBytes, fmt, request.pipeline)
            fmt in AUDIO_FORMAT_EXTENSIONS -> executeAudioTransform(inputBytes, fmt, request.pipeline)
            else -> executeVideoTransform(inputBytes, fmt, request.pipeline)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun executeImageTransform(
        input: dev.transmute.model.core.Bytes,
        formatStr: String,
        pipeline: List<TransformStep>,
    ): ByteArray {
        val format = toImageFormat(formatStr)
        val transforms = buildTransformInstances(ImageTransforms, pipeline) as List<ImageTransform>
        return transmute.image.to(format) {
            transform { transforms.forEach { add(it) } }
        }.transmute(input).bytes.data
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun executeAudioTransform(
        input: dev.transmute.model.core.Bytes,
        formatStr: String,
        pipeline: List<TransformStep>,
    ): ByteArray {
        val format = toAudioFormat(formatStr)
        val transforms = buildTransformInstances(AudioTransforms, pipeline) as List<AudioTransform>
        return transmute.audio.to(format) {
            transform { transforms.forEach { add(it) } }
        }.transmute(input).bytes.data
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun executeVideoTransform(
        input: dev.transmute.model.core.Bytes,
        formatStr: String,
        pipeline: List<TransformStep>,
    ): ByteArray {
        val format = toVideoFormat(formatStr)
        val transforms = buildTransformInstances(VideoTransforms, pipeline) as List<VideoTransform>
        return transmute.video.to(format) {
            transform { transforms.forEach { add(it) } }
        }.transmute(input).bytes.data
    }

    /**
     * Builds a list of transform instances from [steps] by matching each
     * [TransformStep.transformId] to a [TransformDescriptor]-annotated function on
     * [factory] and invoking it via [kotlin.reflect.KCallable.callBy].
     *
     * Parameters supplied by the caller are type-coerced from strings; parameters
     * absent from [TransformStep.parameters] fall back to the factory function's
     * Kotlin default value (if any).
     */
    private fun buildTransformInstances(factory: Any, steps: List<TransformStep>): List<Any> {
        val fns = factory::class.memberFunctions.filter { it.findAnnotation<TransformDescriptor>() != null }
        return steps.mapNotNull { step ->
            val fn = fns.find { fn -> fn.findAnnotation<TransformDescriptor>()?.id == step.transformId }
                ?: run {
                    log.warn("Unknown transform id '${step.transformId}' — skipping")
                    return@mapNotNull null
                }

            val argMap = mutableMapOf<KParameter, Any?>(fn.instanceParameter!! to factory)
            fn.parameters.drop(1).forEach { param ->
                val paramName = param.name ?: return@forEach
                val rawValue = step.parameters[paramName]
                if (rawValue != null) {
                    val parsed = parseParamValue(rawValue, param)
                    if (parsed != null) argMap[param] = parsed
                }
                // If rawValue is null and param.isOptional: callBy uses the Kotlin default.
                // If rawValue is null and !param.isOptional: callBy will throw a meaningful error.
            }

            try {
                fn.callBy(argMap)
            } catch (e: Exception) {
                val root = e.cause ?: e
                log.warn("Failed to instantiate transform '${step.transformId}': ${root.message}")
                null
            }
        }
    }

    /**
     * Coerces a raw string [value] to the Kotlin type expected by [param].
     *
     * Handles primitives (Int, Long, Float, Double, Boolean), IntArray, enum constants,
     * and falls back to String for all other types.
     */
    private fun parseParamValue(value: String, param: KParameter): Any? {
        // For nullable types (Long?, Float?, etc.) Kotlin reflection keeps the
        // classifier set to the underlying class, so we use it directly.
        val classifier = param.type.classifier

        return try {
            when {
                classifier == Int::class -> value.toInt()
                classifier == Long::class -> value.toLong()
                classifier == Float::class -> value.toFloat()
                classifier == Double::class -> value.toDouble()
                classifier == Boolean::class -> value.lowercase() == "true"
                classifier == IntArray::class -> value.split(",").map { it.trim().toInt() }.toIntArray()
                classifier is KClass<*> && classifier.java.isEnum ->
                    classifier.java.enumConstants.first { (it as Enum<*>).name.equals(value, ignoreCase = true) }
                else -> value.ifEmpty { null }
            }
        } catch (e: Exception) {
            log.warn("Could not parse param '${param.name}' value='$value': ${e.message}")
            null
        }
    }

    // -- Cleanup ----------------------------------------------------------------

    fun cleanup() {
        transmute.close()
        tempDir.deleteRecursively()
    }

    // -- Format helpers --------------------------------------------------------

    companion object {
        /** Extension strings that map to the image domain. */
        private val IMAGE_FORMAT_EXTENSIONS =
            setOf("jpeg", "jpg", "png", "webp", "heif", "heic", "avif", "gif", "bmp", "tiff")

        /** Extension strings that map to the audio domain. */
        private val AUDIO_FORMAT_EXTENSIONS =
            setOf("wav", "mp3", "aac", "m4a", "flac", "ogg", "opus")

        /** Extension strings that map to the video domain. */
        private val VIDEO_FORMAT_EXTENSIONS =
            setOf("mp4", "mov", "webm", "mkv", "avi")

        private fun toImageFormat(s: String): ImageFormat = when (s) {
            "jpeg", "jpg" -> ImageFormat.Jpeg
            "png" -> ImageFormat.Png
            "webp" -> ImageFormat.Webp
            "heif" -> ImageFormat.Heif
            "heic" -> ImageFormat.Heic
            "avif" -> ImageFormat.Avif
            "gif" -> ImageFormat.Gif
            "bmp" -> ImageFormat.Bmp
            "tiff" -> ImageFormat.Tiff
            else -> throw IllegalArgumentException("Unsupported image format: $s")
        }

        private fun toAudioFormat(s: String): AudioFormat = when (s) {
            "wav" -> AudioFormat.Wav
            "mp3" -> AudioFormat.Mp3
            "aac" -> AudioFormat.Aac
            "m4a" -> AudioFormat.M4a
            "flac" -> AudioFormat.Flac
            "ogg" -> AudioFormat.Ogg
            "opus" -> AudioFormat.Opus
            else -> throw IllegalArgumentException("Unsupported audio format: $s")
        }

        private fun toVideoFormat(s: String): VideoFormat = when (s) {
            "mp4" -> VideoFormat.Mp4
            "mov" -> VideoFormat.Mov
            "webm" -> VideoFormat.Webm
            "mkv" -> VideoFormat.Mkv
            "avi" -> VideoFormat.Avi
            else -> throw IllegalArgumentException("Unsupported video format: $s")
        }
    }
}

data class UploadedFile(
    val handle: String,
    val name: String,
    val size: Long,
    val file: File,
)
