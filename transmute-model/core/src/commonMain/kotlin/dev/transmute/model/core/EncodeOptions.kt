package dev.transmute.model.core

/**
 * Marker interface for encoder configuration.
 *
 * Encoding settings should be passed explicitly via an [EncodeOptions] instance
 * rather than being embedded in the IR or relying on global mutable state.
 */
interface EncodeOptions

/**
 * Default options for encoders that do not require any configuration.
 */
data object NoEncodeOptions : EncodeOptions
