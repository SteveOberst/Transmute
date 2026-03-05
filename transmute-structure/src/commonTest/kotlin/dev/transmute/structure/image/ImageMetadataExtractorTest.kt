package dev.transmute.structure.image

import dev.transmute.model.core.Bytes
import dev.transmute.model.identify.FourCC
import dev.transmute.model.identify.RiffChunkId
import dev.transmute.model.metadata.png.PngTextMetadata
import dev.transmute.model.metadata.png.PngTextChunk
import dev.transmute.model.metadata.xmp.XmpMetadata
import dev.transmute.model.metadata.xmp.XmpNode
import dev.transmute.model.structure.common.RiffChunk
import dev.transmute.model.structure.image.types.JpegRaw
import dev.transmute.model.structure.image.types.JpegSegment
import dev.transmute.model.structure.image.types.PngChunk
import dev.transmute.model.structure.image.types.PngRaw
import dev.transmute.model.structure.image.types.WebpRaw
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for image format metadata extractors.
 *
 * Covers: JPEG (basic extraction + empty), PNG (tEXt + iTXt + XMP + empty),
 * WebP (XMP + empty).
 *
 * Note: EXIF and ICC profile extraction tests require complex binary
 * fixtures (valid TIFF structures, 128+ byte ICC headers). The shared
 * helpers [parseIccProfile], [tiffRawToExif], and [parseXmpText] are tested
 * through integration with real files in instrumented tests. Here we test
 * the wiring and simpler extraction paths.
 */
class ImageMetadataExtractorTest {

  // ===========================================================================
  //  PNG - tEXt extraction
  // ===========================================================================

  /** Build a PNG tEXt chunk: keyword + null separator + text. */
  private fun pngTextChunk(keyword: String, text: String): PngChunk {
    val payload = (keyword + "\u0000" + text).encodeToByteArray()
    return PngChunk(
      length = payload.size.toUInt(),
      type = FourCC("tEXt"),
      data = Bytes(payload),
      crc = 0u,
    )
  }

  /** Build a PNG iTXt chunk: keyword + null + compression(0) + method(0) + lang + null + transKey + null + text. */
  private fun pngItxtChunk(keyword: String, text: String, language: String = "", translatedKeyword: String = ""): PngChunk {
    val buf = mutableListOf<Byte>()
    buf.addAll(keyword.encodeToByteArray().toList())
    buf.add(0) // null separator
    buf.add(0) // compression flag (0 = uncompressed)
    buf.add(0) // compression method
    buf.addAll(language.encodeToByteArray().toList())
    buf.add(0) // null separator
    buf.addAll(translatedKeyword.encodeToByteArray().toList())
    buf.add(0) // null separator
    buf.addAll(text.encodeToByteArray().toList())
    val payload = buf.toByteArray()
    return PngChunk(
      length = payload.size.toUInt(),
      type = FourCC("iTXt"),
      data = Bytes(payload),
      crc = 0u,
    )
  }

  private val pngSignature = Bytes(
    byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
  )

  @Test
  fun pngExtractsTextChunks() {
    val png = PngRaw(
      signature = pngSignature,
      chunks = listOf(
        pngTextChunk("Title", "My Image"),
        pngTextChunk("Author", "Test User"),
      ),
    )
    val metadata = png.extractMetadata()
    val textMeta = metadata.filterIsInstance<PngTextMetadata>().firstOrNull()
    assertNotNull(textMeta)
    assertEquals(2, textMeta.chunks.size)
    val c0 = textMeta.chunks[0]
    assertTrue(c0 is PngTextChunk.Text)
    c0 as PngTextChunk.Text
    assertEquals("Title", c0.keyword.value)
    assertEquals("My Image", c0.text.value)
    val c1 = textMeta.chunks[1]
    assertTrue(c1 is PngTextChunk.Text)
    assertEquals("Author", (c1 as PngTextChunk.Text).keyword.value)
  }

  @Test
  fun pngExtractsItxtChunks() {
    val png = PngRaw(
      signature = pngSignature,
      chunks = listOf(
        pngItxtChunk("Description", "A test image", language = "en", translatedKeyword = "Beschreibung"),
      ),
    )
    val metadata = png.extractMetadata()
    val textMeta = metadata.filterIsInstance<PngTextMetadata>().firstOrNull()
    assertNotNull(textMeta)
    assertEquals(1, textMeta.chunks.size)
    val c0 = textMeta.chunks[0]
    assertTrue(c0 is PngTextChunk.IText)
    c0 as PngTextChunk.IText
    assertEquals("Description", c0.keyword.value)
    assertEquals("A test image", c0.text?.value)
    assertEquals(false, c0.compressed)
    assertEquals("en", c0.languageTag?.value)
    assertEquals("Beschreibung", c0.translatedKeyword?.value)
  }

  @Test
  fun pngExtractsXmpFromItxt() {
    val xmpPayload =
      """
      <?xml version="1.0"?>
      <x:xmpmeta xmlns:x="adobe:ns:meta/">
        <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
          <rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:title>Test</dc:title>
          </rdf:Description>
        </rdf:RDF>
      </x:xmpmeta>
      """.trimIndent()
    val png = PngRaw(
      signature = pngSignature,
      chunks = listOf(
        pngItxtChunk("XML:com.adobe.xmp", xmpPayload),
      ),
    )
    val metadata = png.extractMetadata()
    val xmp = metadata.filterIsInstance<XmpMetadata>().firstOrNull()
    assertNotNull(xmp)
    assertEquals("xmpmeta", xmp.document.root.name.localName)
    assertEquals("adobe:ns:meta/", xmp.document.root.name.namespaceUri)

    // Verify the parsed tree structure: xmpmeta > RDF > Description > title
    val rdf = xmp.document.root.children.filterIsInstance<XmpNode.Element>().firstOrNull()
    assertNotNull(rdf, "expected RDF child element")
    assertEquals("RDF", rdf.element.name.localName)

    val desc = rdf.element.children.filterIsInstance<XmpNode.Element>().firstOrNull()
    assertNotNull(desc, "expected Description child element")
    assertEquals("Description", desc.element.name.localName)

    val title = desc.element.children.filterIsInstance<XmpNode.Element>().firstOrNull()
    assertNotNull(title, "expected dc:title child element")
    assertEquals("title", title.element.name.localName)

    val titleText = title.element.children.filterIsInstance<XmpNode.Text>().firstOrNull()
    assertNotNull(titleText, "expected text content inside dc:title")
    assertEquals("Test", titleText.content)

    // XMP iTXt chunk should NOT also appear in PngTextMetadata
    val textMeta = metadata.filterIsInstance<PngTextMetadata>()
    assertTrue(
      textMeta.all { pm ->
        pm.chunks.none { ch ->
          (ch is PngTextChunk.IText && ch.keyword.value == "XML:com.adobe.xmp") ||
            (ch is PngTextChunk.Text && ch.keyword.value == "XML:com.adobe.xmp")
        }
      },
    )
  }

  @Test
  fun pngEmptyChunksReturnsEmpty() {
    val png = PngRaw(
      signature = pngSignature,
      chunks = emptyList(),
    )
    assertTrue(png.extractMetadata().isEmpty())
  }

  // ===========================================================================
  //  JPEG - basic extraction tests
  // ===========================================================================

  @Test
  fun jpegXmpExtraction() {
    val xmpHeader = "http://ns.adobe.com/xap/1.0/\u0000".encodeToByteArray()
    val xmpPayload =
      """
      <?xml version="1.0"?>
      <x:xmpmeta xmlns:x="adobe:ns:meta/">
        <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
          <rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:creator>Tester</dc:creator>
          </rdf:Description>
        </rdf:RDF>
      </x:xmpmeta>
      """.trimIndent()
    val segData = xmpHeader + xmpPayload.encodeToByteArray()
    val jpeg = JpegRaw(
      headerSegments = listOf(
        JpegSegment(marker = 0xE1u, data = Bytes(segData)),
      ),
    )
    val metadata = jpeg.extractMetadata()
    val xmp = metadata.filterIsInstance<XmpMetadata>().firstOrNull()
    assertNotNull(xmp)
    assertEquals("xmpmeta", xmp.document.root.name.localName)
    assertEquals("adobe:ns:meta/", xmp.document.root.name.namespaceUri)

    // Verify tree: xmpmeta > RDF > Description > creator > "Tester"
    val rdf = xmp.document.root.children.filterIsInstance<XmpNode.Element>().firstOrNull()
    assertNotNull(rdf)
    val desc = rdf?.element?.children?.filterIsInstance<XmpNode.Element>()?.firstOrNull()
    assertNotNull(desc)
    val creator = desc?.element?.children?.filterIsInstance<XmpNode.Element>()?.firstOrNull()
    assertNotNull(creator)
    assertEquals("creator", creator?.element?.name?.localName)
    val creatorText = creator?.element?.children?.filterIsInstance<XmpNode.Text>()?.firstOrNull()
    assertEquals("Tester", creatorText?.content)
  }

  @Test
  fun jpegEmptySegmentsReturnsEmpty() {
    val jpeg = JpegRaw()
    assertTrue(jpeg.extractMetadata().isEmpty())
  }

  @Test
  fun jpegIgnoresNonExifApp1() {
    val jpeg = JpegRaw(
      headerSegments = listOf(
        JpegSegment(marker = 0xE1u, data = Bytes("Not EXIF data".encodeToByteArray())),
      ),
    )
    // Should not crash and should return empty (no EXIF header match)
    val metadata = jpeg.extractMetadata()
    assertTrue(metadata.filterIsInstance<dev.transmute.model.metadata.exif.ExifMetadata>().isEmpty())
  }

  // ===========================================================================
  //  WebP - XMP extraction
  // ===========================================================================

  @Test
  fun webpExtractsXmpChunk() {
    val xmpPayload =
      """
      <?xml version="1.0"?>
      <x:xmpmeta xmlns:x="adobe:ns:meta/">
        <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
          <rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:title>WebP Test</dc:title>
          </rdf:Description>
        </rdf:RDF>
      </x:xmpmeta>
      """.trimIndent()
    val xmpChunk = RiffChunk(
      id = RiffChunkId("XMP "),
      size = xmpPayload.length.toUInt(),
      data = Bytes(xmpPayload.encodeToByteArray()),
    )
    val riff = RiffChunk(
      id = RiffChunkId("RIFF"),
      size = 0u,
      formType = RiffChunkId("WEBP"),
      children = listOf(xmpChunk),
    )
    val webp = WebpRaw(riff = riff)
    val metadata = webp.extractMetadata()
    val xmp = metadata.filterIsInstance<XmpMetadata>().firstOrNull()
    assertNotNull(xmp)
    assertEquals("xmpmeta", xmp.document.root.name.localName)
    assertEquals("adobe:ns:meta/", xmp.document.root.name.namespaceUri)

    // Verify tree depth: xmpmeta > RDF > Description > title > text
    val rdfNode = xmp.document.root.children.filterIsInstance<XmpNode.Element>().firstOrNull()
    assertNotNull(rdfNode)
    assertEquals("RDF", rdfNode.element.name.localName)
    val descNode = rdfNode.element.children.filterIsInstance<XmpNode.Element>().firstOrNull()
    assertNotNull(descNode)
    val titleNode = descNode.element.children.filterIsInstance<XmpNode.Element>().firstOrNull()
    assertNotNull(titleNode)
    assertEquals("title", titleNode.element.name.localName)
    val text = titleNode.element.children.filterIsInstance<XmpNode.Text>().firstOrNull()
    assertEquals("WebP Test", text?.content)
  }

  @Test
  fun webpEmptyChunksReturnsEmpty() {
    val riff = RiffChunk(
      id = RiffChunkId("RIFF"),
      size = 0u,
      formType = RiffChunkId("WEBP"),
      children = emptyList(),
    )
    assertTrue(WebpRaw(riff = riff).extractMetadata().isEmpty())
  }
}
