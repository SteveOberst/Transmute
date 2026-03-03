package dev.transmute.model.identify

import kotlinx.serialization.Serializable

/**
 * RIFF chunk identifier (4 ASCII bytes), e.g. `RIFF`, `fmt `, `data`.
 */
@Serializable
data class RiffChunkId(
    val value: String,
) {
    init {
        require(value.length == 4) { "RiffChunkId must be exactly 4 characters: '$value'" }
    }

    override fun toString(): String = value
}
