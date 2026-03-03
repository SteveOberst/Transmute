package dev.transmute.model.identify

import kotlinx.serialization.Serializable

/**
 * EBML element identifier.
 */
@Serializable
data class EbmlId(
    val value: Long,
) {
    override fun toString(): String = "0x" + value.toString(16)
}
