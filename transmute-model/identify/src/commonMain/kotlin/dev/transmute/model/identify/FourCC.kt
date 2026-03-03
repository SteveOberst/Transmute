package dev.transmute.model.identify

import kotlinx.serialization.Serializable

/**
 * Four-character code (4 ASCII bytes) used by containers like ISO BMFF and PNG.
 */
@Serializable
data class FourCC(
    val value: String,
) {
    init {
        require(value.length == 4) { "FourCC must be exactly 4 characters: '$value'" }
    }

    override fun toString(): String = value
}
