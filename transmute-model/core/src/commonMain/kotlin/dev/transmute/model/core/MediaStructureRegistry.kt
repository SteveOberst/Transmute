package dev.transmute.model.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlin.reflect.KClass

/**
 * Dynamic registry that maps string typeIds to [KSerializer] instances for
 * [MediaStructure] implementations.
 *
 * This powers [MediaStructureSerializer]'s polymorphic dispatch. The wire
 * format uses a `type` field (the registered typeId) to select the correct
 * concrete serializer at runtime, without relying on `SerializersModule`.
 *
 * ## typeId conventions
 * - Built-in types: `"transmute.png"`, `"transmute.jpeg"`, `"transmute.wav"`, …
 * - Plugin types: `"<plugin-id>.<format>"`, e.g. `"myplugin.customformat"`
 * - **Never** use class names: they change with refactors.
 *
 * ## Thread safety
 * [register] must be called during app initialization (inside
 * `Transmute.Builder.build()` or a `TransmutePlugin.install()`) before
 * any concurrent reads. Lookups via [serializerFor] and [typeIdFor] are
 * safe to call from any thread after initialization.
 */
object MediaStructureRegistry {

    private val serializerByTypeId = HashMap<String, KSerializer<out MediaStructure>>()
    private val typeIdByClass = HashMap<KClass<out MediaStructure>, String>()

    /**
     * Register a [serializer] for a concrete [MediaStructure] type identified
     * by [typeId], associating it with its Kotlin [klass] for reverse lookup.
     *
     * @throws SerializationException if [typeId] is already registered for a
     *   different class (pass [override] = `true` to replace intentionally).
     */
    fun <S : MediaStructure> register(
        typeId: String,
        serializer: KSerializer<S>,
        klass: KClass<S>,
        override: Boolean = false,
    ) {
        val existing = serializerByTypeId[typeId]
        if (existing != null && !override) {
            val existingClass = typeIdByClass.entries.firstOrNull { it.value == typeId }?.key
            if (existingClass != klass) {
                throw SerializationException(
                    "MediaStructureRegistry: typeId '$typeId' is already registered for " +
                        "${existingClass?.simpleName}. Pass override=true to replace."
                )
            }
        }
        serializerByTypeId[typeId] = serializer
        typeIdByClass[klass] = typeId
    }

    /**
     * Convenience [register] overload using a reified type parameter to capture
     * the class automatically.
     *
     * ```kotlin
     * MediaStructureRegistry.register<PngStructure>("transmute.png", PngStructure.serializer())
     * ```
     */
    inline fun <reified S : MediaStructure> register(
        typeId: String,
        serializer: KSerializer<S>,
        override: Boolean = false,
    ) = register(typeId, serializer, S::class, override)

    /**
     * Look up the serializer for [typeId], or `null` if not registered.
     */
    fun serializerFor(typeId: String): KSerializer<out MediaStructure>? =
        serializerByTypeId[typeId]

    /**
     * Look up the typeId registered for [value]'s concrete class, or `null`
     * if the class was not registered.
     */
    fun typeIdFor(value: MediaStructure): String? = typeIdByClass[value::class]

    /**
     * All currently registered typeIds.
     */
    val registeredTypeIds: Set<String> get() = serializerByTypeId.keys.toSet()

    /**
     * Remove all registrations. Primarily useful for tests that need a clean
     * slate between test runs.
     */
    fun clear() {
        serializerByTypeId.clear()
        typeIdByClass.clear()
    }
}
