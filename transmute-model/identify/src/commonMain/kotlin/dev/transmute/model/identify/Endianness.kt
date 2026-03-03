package dev.transmute.model.identify

import kotlinx.serialization.Serializable

@Serializable
enum class Endianness {
    Little,
    Big,
}
