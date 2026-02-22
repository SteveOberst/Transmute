package dev.transmute.core

/**
 * Marker interface for decoder configuration.
 *
 * Decoding hints should be passed explicitly via a [DecodeOptions] instance
 * rather than being embedded in the input bytes or IR.
 *
 * Most decoders accept [NoDecodeOptions]; format-specific options
 * (e.g. JPEG downscale hints) extend this interface.
 */
interface DecodeOptions

/**
 * Default options for decoders that do not require any configuration.
 */
data object NoDecodeOptions : DecodeOptions
