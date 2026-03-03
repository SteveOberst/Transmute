package dev.transmute.model.identify

import kotlinx.serialization.Serializable

/**
 * ISO BMFF brand identifier (a FourCC).
 */
@Serializable
data class Brand(
    val value: FourCC,
) {
    override fun toString(): String = value.value
}
