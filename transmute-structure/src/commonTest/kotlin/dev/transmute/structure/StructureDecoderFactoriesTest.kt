package dev.transmute.structure

import dev.transmute.audio.AudioFormat
import dev.transmute.image.ImageFormat
import dev.transmute.model.structure.image.toStructure
import dev.transmute.structure.audio.WavStructureReader
import dev.transmute.structure.image.PngStructureReader
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [rawDecoderFor] and [structureDecoderFor] factory functions.
 *
 * These tests focus on the [decodableFormats] contract because the `decode` path
 * simply delegates to the wrapped [StructureReader.read], which has its own
 * per-format tests.
 */
class StructureDecoderFactoriesTest {

  // -- rawDecoderFor ---------------------------------------------------------

  @Test
  fun rawDecoderForReportsCorrectDecodableFormats() {
    val decoder = rawDecoderFor(AudioFormat.Wav, WavStructureReader())
    assertEquals(setOf(AudioFormat.Wav), decoder.decodableFormats)
  }

  // -- structureDecoderFor ---------------------------------------------------

  @Test
  fun structureDecoderForReportsCorrectDecodableFormats() {
    val decoder = structureDecoderFor(ImageFormat.Png, PngStructureReader()) { toStructure() }
    assertEquals(setOf(ImageFormat.Png), decoder.decodableFormats)
  }
}
