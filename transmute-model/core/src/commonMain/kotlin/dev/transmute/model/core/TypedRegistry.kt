package dev.transmute.model.core

import kotlin.reflect.KClass
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// -- Generic typed registry --------------------------------------------------

/**
 * Dynamic registry that maps string [typeId]s to [KSerializer] instances
 * for concrete subtypes of [T].
 *
 * This powers the `{ "type": "...", "value": { ... } }` JSON envelope pattern
 * used by both [MediaStructure] and [MediaMetadata].
 *
 * ## typeId conventions
 * - Built-in types: `"transmute.png"`, `"transmute.exif"`, ...
 * - Plugin types:   `"<plugin-id>.<kind>"`, e.g. `"myplugin.customformat"`
 * - **Never** use class names: they change with refactors.
 *
 * ## Thread safety
 * [register] must be called during app initialisation (inside
 * `Transmute.Builder.build()` or a `TransmutePlugin.install()`) before
 * any concurrent reads. Lookups via [serializerFor] and [typeIdFor] are
 * safe to call from any thread after initialisation.
 */
open class TypedRegistry<T : Any>(private val registryName: String) {

  private val serializerByTypeId = HashMap<String, KSerializer<out T>>()
  private val typeIdByClass = HashMap<KClass<out T>, String>()

  /**
   * Register a [serializer] for a concrete [T] type identified by [typeId],
   * associating it with its Kotlin [klass] for reverse lookup.
   *
   * @throws SerializationException if [typeId] is already registered for a
   *   different class (pass [override] = `true` to replace intentionally).
   */
  fun <S : T> register(typeId: String, serializer: KSerializer<S>, klass: KClass<S>, override: Boolean = false) {
    val existing = serializerByTypeId[typeId]
    if (existing != null && !override) {
      val existingClass = typeIdByClass.entries.firstOrNull { it.value == typeId }?.key
      if (existingClass != klass) {
        throw SerializationException(
          "$registryName: typeId '$typeId' is already registered for " +
            "${existingClass?.simpleName}. Pass override=true to replace.",
        )
      }
    }
    serializerByTypeId[typeId] = serializer
    typeIdByClass[klass] = typeId
  }

  /**
   * Convenience overload using a reified type parameter to capture the class.
   */
  inline fun <reified S : T> register(typeId: String, serializer: KSerializer<S>, override: Boolean = false) =
    register(typeId, serializer, S::class, override)

  /** Look up the serializer for [typeId], or `null` if not registered. */
  fun serializerFor(typeId: String): KSerializer<out T>? = serializerByTypeId[typeId]

  /** Look up the typeId registered for [value]'s concrete class. */
  fun typeIdFor(value: T): String? = typeIdByClass[value::class]

  /** All currently registered typeIds. */
  val registeredTypeIds: Set<String> get() = serializerByTypeId.keys.toSet()

  /** Remove all registrations. Primarily useful for tests. */
  fun clear() {
    serializerByTypeId.clear()
    typeIdByClass.clear()
  }
}

// -- Generic typed-envelope serializer ---------------------------------------

/**
 * Polymorphic [KSerializer] that wraps any registered [T] subtype in a
 * `{ "type": "...", "value": { ... } }` JSON envelope, dispatching via
 * a [TypedRegistry].
 *
 * **JSON only** - requires [JsonEncoder]/[JsonDecoder] and will throw
 * [SerializationException] if used with another format.
 */
open class TypedEnvelopeSerializer<T : Any>(private val descriptorName: String, private val registry: TypedRegistry<T>) : KSerializer<T> {

  override val descriptor: SerialDescriptor =
    buildClassSerialDescriptor(descriptorName) {
      element<String>("type")
      element<JsonElement>("value")
    }

  override fun serialize(encoder: Encoder, value: T) {
    require(encoder is JsonEncoder) {
      "$descriptorName serializer only supports JSON encoding. " +
        "Got: ${encoder::class.simpleName}"
    }

    val typeId = registry.typeIdFor(value)
      ?: throw SerializationException(
        "No typeId registered for ${value::class.simpleName}. " +
          "Register it before encoding.",
      )

    @Suppress("UNCHECKED_CAST")
    val concreteSerializer = registry.serializerFor(typeId)!! as KSerializer<T>
    val valueElement = encoder.json.encodeToJsonElement(concreteSerializer, value)

    encoder.encodeJsonElement(
      buildJsonObject {
        put("type", typeId)
        put("value", valueElement)
      },
    )
  }

  override fun deserialize(decoder: Decoder): T {
    require(decoder is JsonDecoder) {
      "$descriptorName serializer only supports JSON decoding. " +
        "Got: ${decoder::class.simpleName}"
    }

    val jsonObject = decoder.decodeJsonElement().jsonObject

    val typeId = jsonObject["type"]?.jsonPrimitive?.content
      ?: throw SerializationException(
        "Missing 'type' field in $descriptorName JSON object.",
      )

    val serializer = registry.serializerFor(typeId)
      ?: throw SerializationException(
        "Unknown $descriptorName typeId: '$typeId'. " +
          "Known types: ${registry.registeredTypeIds.sorted()}",
      )

    val valueElement = jsonObject["value"]
      ?: throw SerializationException(
        "Missing 'value' field in $descriptorName JSON object for type '$typeId'.",
      )

    @Suppress("UNCHECKED_CAST")
    return decoder.json.decodeFromJsonElement(
      serializer as KSerializer<T>,
      valueElement,
    )
  }
}

// -- Generic registration scope (for plugin API) ----------------------------

/**
 * Thin, scoped wrapper over a [TypedRegistry] that plugins use to register
 * their serialisable types during [install][dev.transmute.plugin.TransmutePlugin.install].
 */
open class TypedRegistrationScope<T : Any>(@PublishedApi internal val registry: TypedRegistry<T>) {

  inline fun <reified S : T> register(typeId: String, serializer: KSerializer<S>, override: Boolean = false) {
    registry.register(typeId, serializer, override)
  }
}
