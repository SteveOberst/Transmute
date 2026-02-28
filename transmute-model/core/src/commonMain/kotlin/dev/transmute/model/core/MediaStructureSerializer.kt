package dev.transmute.model.core

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

/**
 * Polymorphic [KSerializer] for [MediaStructure].
 *
 * Handles serialization of any registered [MediaStructure] subtype using a
 * `type` + `value` JSON envelope:
 * ```json
 * {
 *   "type": "transmute.png",
 *   "value": {
 *     "ihdr": { "width": 1920, "height": 1080, ... },
 *     ...
 *   }
 * }
 * ```
 *
 * Concrete types must be registered in [MediaStructureRegistry] before this
 * serializer is used. Built-in types are registered automatically when a
 * [dev.transmute.Transmute] instance is built.
 *
 * **JSON only** — this serializer requires a [JsonEncoder]/[JsonDecoder] and
 * will throw [SerializationException] if used with another format.
 */
object MediaStructureSerializer : KSerializer<MediaStructure> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("MediaStructure") {
            element<String>("type")
            element<JsonElement>("value")
        }

    override fun serialize(encoder: Encoder, value: MediaStructure) {
        require(encoder is JsonEncoder) {
            "MediaStructureSerializer only supports JSON encoding. " +
                "Got: ${encoder::class.simpleName}"
        }

        val typeId = MediaStructureRegistry.typeIdFor(value)
            ?: throw SerializationException(
                "No typeId registered for ${value::class.simpleName}. " +
                    "Register it with MediaStructureRegistry.register() before encoding."
            )

        @Suppress("UNCHECKED_CAST")
        val concreteSerializer = MediaStructureRegistry.serializerFor(typeId)!!
            as KSerializer<MediaStructure>

        val valueElement = encoder.json.encodeToJsonElement(concreteSerializer, value)

        encoder.encodeJsonElement(buildJsonObject {
            put("type", typeId)
            put("value", valueElement)
        })
    }

    override fun deserialize(decoder: Decoder): MediaStructure {
        require(decoder is JsonDecoder) {
            "MediaStructureSerializer only supports JSON decoding. " +
                "Got: ${decoder::class.simpleName}"
        }

        val jsonObject = decoder.decodeJsonElement().jsonObject

        val typeId = jsonObject["type"]?.jsonPrimitive?.content
            ?: throw SerializationException(
                "Missing 'type' field in MediaStructure JSON object."
            )

        val serializer = MediaStructureRegistry.serializerFor(typeId)
            ?: throw SerializationException(
                "Unknown MediaStructure typeId: '$typeId'. " +
                    "Known types: ${MediaStructureRegistry.registeredTypeIds.sorted()}"
            )

        val valueElement = jsonObject["value"]
            ?: throw SerializationException(
                "Missing 'value' field in MediaStructure JSON object for type '$typeId'."
            )

        @Suppress("UNCHECKED_CAST")
        return decoder.json.decodeFromJsonElement(
            serializer as KSerializer<MediaStructure>,
            valueElement,
        )
    }
}
