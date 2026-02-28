package dev.transmute.playground

import dev.transmute.Transmute
import dev.transmute.transmute
import dev.transmute.gstreamer.GStreamer
import dev.transmute.playground.shared.*
import dev.transmute.plugin.PluginId
import dev.transmute.image.ImageFormat
import dev.transmute.audio.AudioFormat
import dev.transmute.video.VideoFormat
import dev.transmute.model.structure.StructureReaders
import dev.transmute.AudioTransforms
import dev.transmute.ImageTransforms
import dev.transmute.Param
import dev.transmute.TransformDescriptor
import dev.transmute.VideoTransforms
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions

/**
 * Owns the Transmute instance and manages uploaded files.
 *
 * All media processing operations flow through this service.
 * Plugin and format information is derived **dynamically** from the
 * Transmute instance's codec registries — nothing is hardcoded.
 */
class TransmuteService(
    private val tempDir: File = File(System.getProperty("java.io.tmpdir"), "transmute-playground"),
) {
    private val files = ConcurrentHashMap<String, UploadedFile>()

    // -- Plugin management (dynamic) -------------------------------------------

    private val featureOverrides = mutableMapOf<String, Boolean>()
    private val disabledPlugins = mutableSetOf<PluginId>()

    /** All plugins that this playground knows about, keyed by plugin ID string. */
    private val knownPlugins: Map<String, PluginId> = mapOf(
        GStreamer.key.id to GStreamer.key,
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
            InspectResult(
                domain = domain,
                format = format.label,
                fileSize = uploaded.size,
                properties = buildMap {
                    put("filename", uploaded.name)
                    put("format", format.label)
                    put("domain", domain.name.lowercase())
                    put("fileSize", humanReadableSize(uploaded.size))
                    format::class.simpleName?.let { put("decodedBy", it) }
                },
            )
        } catch (e: Exception) {
            InspectResult(
                domain = MediaDomainDto.IMAGE,
                format = "error",
                fileSize = uploaded.size,
                properties = mapOf("error" to (e.message ?: "Unknown error")),
            )
        }
    }

    // -- Dynamic format catalog ------------------------------------------------

    fun allFormats(): List<FormatInfo> = imageFormats() + audioFormats() + videoFormats()

    fun imageFormats(): List<FormatInfo> {
        val decodable = transmute.codec.image.decodableFormats
        val encodable = transmute.codec.image.encodableFormats
        val allKnown = (decodable + encodable).filterNot { it is ImageFormat.Unknown }
        return allKnown.map { fmt ->
            FormatInfo(
                name = fmt.label,
                domain = MediaDomainDto.IMAGE,
                canDecode = fmt in decodable,
                canEncode = fmt in encodable,
                hasStructureReader = StructureReaders.readerFor<Nothing>(fmt) != null,
                providedBy = if (fmt.label in builtInFormatLabels) null else GStreamer.key.id,
            )
        }.sortedBy { it.name }
    }

    fun audioFormats(): List<FormatInfo> {
        val decodable = transmute.codec.audio.decodableFormats
        val encodable = transmute.codec.audio.encodableFormats
        val allKnown = (decodable + encodable).filterNot { it is AudioFormat.Unknown }
        return allKnown.map { fmt ->
            FormatInfo(
                name = fmt.label,
                domain = MediaDomainDto.AUDIO,
                canDecode = fmt in decodable,
                canEncode = fmt in encodable,
                hasStructureReader = StructureReaders.readerFor<Nothing>(fmt) != null,
                providedBy = if (fmt.label in builtInFormatLabels) null else GStreamer.key.id,
            )
        }.sortedBy { it.name }
    }

    fun videoFormats(): List<FormatInfo> {
        val decodable = transmute.codec.video.decodableFormats
        val encodable = transmute.codec.video.encodableFormats
        val allKnown = (decodable + encodable).filterNot { it is VideoFormat.Unknown }
        return allKnown.map { fmt ->
            FormatInfo(
                name = fmt.label,
                domain = MediaDomainDto.VIDEO,
                canDecode = fmt in decodable,
                canEncode = fmt in encodable,
                hasStructureReader = StructureReaders.readerFor<Nothing>(fmt) != null,
                providedBy = if (fmt.label in builtInFormatLabels) null else GStreamer.key.id,
            )
        }.sortedBy { it.name }
    }

    // -- Reflection-driven transform catalog ---------------------------------
    //    Transform metadata is discovered at runtime from annotations on the
    //    Transformers.kt factory objects — nothing is hardcoded here.

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
                    // Unwrap nullable wrapper (Long? → Long, etc.)
                    val unwrapped = if (param.type.isMarkedNullable && classifier == null) {
                        param.type.arguments.firstOrNull()?.type?.classifier
                    } else classifier
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
                name = keyId.substringAfterLast('.').replaceFirstChar { it.uppercase() },
                description = "Transmute plugin: $keyId",
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
        (transmute.codec.image.decodableFormats + transmute.codec.image.encodableFormats)
            .filterNot { it is ImageFormat.Unknown }.forEach { add(it.label) }
        (transmute.codec.audio.decodableFormats + transmute.codec.audio.encodableFormats)
            .filterNot { it is AudioFormat.Unknown }.forEach { add(it.label) }
        (transmute.codec.video.decodableFormats + transmute.codec.video.encodableFormats)
            .filterNot { it is VideoFormat.Unknown }.forEach { add(it.label) }
    }.distinct().sorted()

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

    // -- Cleanup ----------------------------------------------------------------

    fun cleanup() {
        transmute.close()
        tempDir.deleteRecursively()
    }
}

data class UploadedFile(
    val handle: String,
    val name: String,
    val size: Long,
    val file: File,
)
