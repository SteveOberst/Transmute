@file:Suppress("unused")

package dev.transmute.model.structure

import dev.transmute.model.core.RawMediaStructure

// The old MediaStructure interface has been superseded by RawMediaStructure
// (dev.transmute.model.core.RawMediaStructure) in the core module.
// All format-specific raw models (PngRaw, WavRaw, Mp4Raw, ...) now extend
// RawMediaStructure directly.
//
// The new MediaStructure (dev.transmute.model.core.MediaStructure) is the
// JSON-safe developer-friendly view of a file's structure. Each format has
// a corresponding *Structure data class in this module.
