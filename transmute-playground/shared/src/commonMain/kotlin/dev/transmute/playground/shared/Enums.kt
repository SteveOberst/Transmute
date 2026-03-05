package dev.transmute.playground.shared

import kotlinx.serialization.Serializable

/** Media domain discriminator shared between server and web. */
@Serializable
enum class MediaDomainDto {
  IMAGE,
  AUDIO,
  VIDEO,
}

/** Parameter type discriminator for dynamic UI rendering. */
@Serializable
enum class ParameterType {
  INT,
  FLOAT,
  BOOLEAN,
  STRING,
  ENUM,
  INT_ARRAY,
}
