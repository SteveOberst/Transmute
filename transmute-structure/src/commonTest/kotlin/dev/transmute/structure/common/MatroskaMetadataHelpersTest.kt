package dev.transmute.structure.common

import dev.transmute.model.core.Bytes
import dev.transmute.model.structure.common.EbmlElement
import dev.transmute.model.structure.video.types.MatroskaIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [extractMatroskaTags] and its internal helpers.
 */
class MatroskaMetadataHelpersTest {

  // -- Helpers to build synthetic EBML trees --------------------------------

  /** Create a leaf element with string data. */
  private fun stringElement(id: Long, value: String) = EbmlElement(
    id = dev.transmute.model.identify.EbmlId(id),
    data = Bytes(value.encodeToByteArray()),
  )

  /** Create a leaf element with a single-byte unsigned integer. */
  private fun uintElement(id: Long, value: Int) = EbmlElement(
    id = dev.transmute.model.identify.EbmlId(id),
    data = Bytes(byteArrayOf(value.toByte())),
  )

  /** Create a binary leaf element. */
  private fun binaryElement(id: Long, data: ByteArray) = EbmlElement(
    id = dev.transmute.model.identify.EbmlId(id),
    data = Bytes(data),
  )

  /** Create a master element with children. */
  private fun master(id: Long, children: List<EbmlElement>) = EbmlElement(
    id = dev.transmute.model.identify.EbmlId(id),
    children = children,
  )

  /** Build a full Segment > Tags > Tag... element tree for extractMatroskaTags. */
  private fun makeSegmentWithTags(tagElements: List<EbmlElement>): List<EbmlElement> {
    val tags = master(MatroskaIds.Tags.value, tagElements)
    val segment = master(MatroskaIds.Segment.value, listOf(tags))
    return listOf(segment)
  }

  /** Build a SimpleTag with name and optional string value. */
  private fun simpleTag(name: String, value: String? = null, binaryData: ByteArray? = null): EbmlElement {
    val children = mutableListOf(stringElement(MatroskaIds.TagName.value, name))
    if (value != null) children.add(stringElement(MatroskaIds.TagString.value, value))
    if (binaryData != null) children.add(binaryElement(MatroskaIds.TagBinary.value, binaryData))
    return master(MatroskaIds.SimpleTag.value, children)
  }

  /** Build a Tag with optional Targets and SimpleTags. */
  private fun tag(targetTypeValue: Int? = null, targetType: String? = null, simpleTags: List<EbmlElement> = emptyList()): EbmlElement {
    val children = mutableListOf<EbmlElement>()
    if (targetTypeValue != null || targetType != null) {
      val targetChildren = mutableListOf<EbmlElement>()
      if (targetTypeValue != null) targetChildren.add(uintElement(MatroskaIds.TargetTypeValue.value, targetTypeValue))
      if (targetType != null) targetChildren.add(stringElement(MatroskaIds.TargetType.value, targetType))
      children.add(master(MatroskaIds.Targets.value, targetChildren))
    }
    children.addAll(simpleTags)
    return master(MatroskaIds.Tag.value, children)
  }

  // -- Tests ----------------------------------------------------------------

  @Test
  fun extractsSingleTagWithOneSimpleTag() {
    val elements = makeSegmentWithTags(
      listOf(
        tag(
          simpleTags = listOf(simpleTag("TITLE", "My Video")),
        ),
      ),
    )
    val result = extractMatroskaTags(elements)
    assertNotNull(result)
    assertEquals(1, result.tags.size)
    assertEquals(1, result.tags[0].simpleTags.size)
    assertEquals("TITLE", result.tags[0].simpleTags[0].name.value)
    assertEquals("My Video", result.tags[0].simpleTags[0].value?.value)
  }

  @Test
  fun extractsMultipleSimpleTags() {
    val elements = makeSegmentWithTags(
      listOf(
        tag(
          simpleTags = listOf(
            simpleTag("TITLE", "Test"),
            simpleTag("ARTIST", "Author"),
            simpleTag("DATE_RELEASED", "2024"),
          ),
        ),
      ),
    )
    val result = extractMatroskaTags(elements)
    assertNotNull(result)
    assertEquals(1, result.tags.size)
    assertEquals(3, result.tags[0].simpleTags.size)
    assertEquals("ARTIST", result.tags[0].simpleTags[1].name.value)
    assertEquals("Author", result.tags[0].simpleTags[1].value?.value)
  }

  @Test
  fun extractsTargetInfo() {
    val elements = makeSegmentWithTags(
      listOf(
        tag(
          targetTypeValue = 50,
          targetType = "ALBUM",
          simpleTags = listOf(simpleTag("TITLE", "Album Name")),
        ),
      ),
    )
    val result = extractMatroskaTags(elements)
    assertNotNull(result)
    assertEquals(50uL, result.tags[0].targets?.targetTypeValue)
    assertEquals("ALBUM", result.tags[0].targets?.targetType?.value)
  }

  @Test
  fun extractsMultipleTags() {
    val elements = makeSegmentWithTags(
      listOf(
        tag(
          targetTypeValue = 50,
          simpleTags = listOf(simpleTag("TITLE", "Album")),
        ),
        tag(
          targetTypeValue = 30,
          simpleTags = listOf(simpleTag("TITLE", "Track")),
        ),
      ),
    )
    val result = extractMatroskaTags(elements)
    assertNotNull(result)
    assertEquals(2, result.tags.size)
    assertEquals(50uL, result.tags[0].targets?.targetTypeValue)
    assertEquals(30uL, result.tags[1].targets?.targetTypeValue)
  }

  @Test
  fun handlesTagWithBinaryData() {
    val binaryData = ByteArray(128) { it.toByte() }
    val elements = makeSegmentWithTags(
      listOf(
        tag(
          simpleTags = listOf(simpleTag("COVER_ART", binaryData = binaryData)),
        ),
      ),
    )
    val result = extractMatroskaTags(elements)
    assertNotNull(result)
    assertEquals(128uL, result.tags[0].simpleTags[0].binary?.sizeBytes)
    assertNull(result.tags[0].simpleTags[0].value)
  }

  @Test
  fun handlesTagLanguage() {
    val children = listOf(
      stringElement(MatroskaIds.TagName.value, "TITLE"),
      stringElement(MatroskaIds.TagString.value, "Titre"),
      stringElement(MatroskaIds.TagLanguage.value, "fre"),
    )
    val simpleTagElement = master(MatroskaIds.SimpleTag.value, children)
    val elements = makeSegmentWithTags(
      listOf(tag(simpleTags = listOf(simpleTagElement))),
    )
    val result = extractMatroskaTags(elements)
    assertNotNull(result)
    assertEquals("fre", result.tags[0].simpleTags[0].language?.value)
  }

  @Test
  fun ignoresUndLanguage() {
    val children = listOf(
      stringElement(MatroskaIds.TagName.value, "TITLE"),
      stringElement(MatroskaIds.TagString.value, "Test"),
      stringElement(MatroskaIds.TagLanguage.value, "und"),
    )
    val simpleTagElement = master(MatroskaIds.SimpleTag.value, children)
    val elements = makeSegmentWithTags(
      listOf(tag(simpleTags = listOf(simpleTagElement))),
    )
    val result = extractMatroskaTags(elements)
    assertNotNull(result)
    assertNull(result.tags[0].simpleTags[0].language)
  }

  @Test
  fun returnsNullWhenNoSegment() {
    val elements = listOf(master(MatroskaIds.EBML.value, emptyList()))
    assertNull(extractMatroskaTags(elements))
  }

  @Test
  fun returnsNullWhenNoTagsElement() {
    val segment = master(
      MatroskaIds.Segment.value,
      listOf(master(MatroskaIds.Info.value, emptyList())),
    )
    assertNull(extractMatroskaTags(listOf(segment)))
  }

  @Test
  fun returnsNullWhenTagsIsEmpty() {
    val elements = makeSegmentWithTags(emptyList())
    assertNull(extractMatroskaTags(elements))
  }

  @Test
  fun returnsNullWhenTagHasNoSimpleTags() {
    // A Tag with only Targets but no SimpleTags should yield null
    val tagEl = master(
      MatroskaIds.Tag.value,
      listOf(master(MatroskaIds.Targets.value, listOf(uintElement(MatroskaIds.TargetTypeValue.value, 50)))),
    )
    val elements = makeSegmentWithTags(listOf(tagEl))
    assertNull(extractMatroskaTags(elements))
  }

  @Test
  fun simpleTagWithoutNameIsSkipped() {
    // SimpleTag that has no TagName child
    val badSimple = master(
      MatroskaIds.SimpleTag.value,
      listOf(stringElement(MatroskaIds.TagString.value, "orphan value")),
    )
    val goodSimple = simpleTag("GOOD", "value")
    val elements = makeSegmentWithTags(
      listOf(tag(simpleTags = listOf(badSimple, goodSimple))),
    )
    val result = extractMatroskaTags(elements)
    assertNotNull(result)
    assertEquals(1, result.tags[0].simpleTags.size)
    assertEquals("GOOD", result.tags[0].simpleTags[0].name.value)
  }

  @Test
  fun targetWithoutTypeValueIsNull() {
    val elements = makeSegmentWithTags(
      listOf(
        tag(
          targetType = "TRACK",
          simpleTags = listOf(simpleTag("TITLE", "Test")),
        ),
      ),
    )
    val result = extractMatroskaTags(elements)
    assertNotNull(result)
    assertNull(result.tags[0].targets?.targetTypeValue)
    assertEquals("TRACK", result.tags[0].targets?.targetType?.value)
  }

  // -- Nested SimpleTags ----------------------------------------------------

  @Test
  fun extractsNestedSimpleTags() {
    val innerTag1 = simpleTag("PART_NUMBER", "1")
    val innerTag2 = simpleTag("PART_NAME", "Introduction")
    val outerChildren = mutableListOf(
      stringElement(MatroskaIds.TagName.value, "CHAPTER"),
      stringElement(MatroskaIds.TagString.value, "Chapter 1"),
    )
    outerChildren.add(innerTag1)
    outerChildren.add(innerTag2)
    val outerTag = master(MatroskaIds.SimpleTag.value, outerChildren)

    val elements = makeSegmentWithTags(
      listOf(tag(simpleTags = listOf(outerTag))),
    )
    val result = extractMatroskaTags(elements)
    assertNotNull(result)
    val st = result.tags[0].simpleTags[0]
    assertEquals("CHAPTER", st.name.value)
    assertEquals("Chapter 1", st.value?.value)
    assertEquals(2, st.children.size)
    assertEquals("PART_NUMBER", st.children[0].name.value)
    assertEquals("1", st.children[0].value?.value)
    assertEquals("PART_NAME", st.children[1].name.value)
    assertEquals("Introduction", st.children[1].value?.value)
    assertTrue(st.children[0].children.isEmpty())
  }

  @Test
  fun leafSimpleTagHasEmptyChildren() {
    val elements = makeSegmentWithTags(
      listOf(tag(simpleTags = listOf(simpleTag("TITLE", "Test")))),
    )
    val result = extractMatroskaTags(elements)
    assertNotNull(result)
    assertTrue(result.tags[0].simpleTags[0].children.isEmpty())
  }

  // -- TagDefault flag ------------------------------------------------------

  @Test
  fun extractsTagDefaultTrue() {
    val children = listOf(
      stringElement(MatroskaIds.TagName.value, "TITLE"),
      stringElement(MatroskaIds.TagString.value, "Default Title"),
      uintElement(MatroskaIds.TagDefault.value, 1),
    )
    val simpleTagElement = master(MatroskaIds.SimpleTag.value, children)
    val elements = makeSegmentWithTags(
      listOf(tag(simpleTags = listOf(simpleTagElement))),
    )
    val result = extractMatroskaTags(elements)
    assertNotNull(result)
    assertEquals(true, result.tags[0].simpleTags[0].default)
  }

  @Test
  fun extractsTagDefaultFalse() {
    val children = listOf(
      stringElement(MatroskaIds.TagName.value, "TITLE"),
      stringElement(MatroskaIds.TagString.value, "Alt Title"),
      uintElement(MatroskaIds.TagDefault.value, 0),
    )
    val simpleTagElement = master(MatroskaIds.SimpleTag.value, children)
    val elements = makeSegmentWithTags(
      listOf(tag(simpleTags = listOf(simpleTagElement))),
    )
    val result = extractMatroskaTags(elements)
    assertNotNull(result)
    assertEquals(false, result.tags[0].simpleTags[0].default)
  }

  @Test
  fun tagDefaultIsNullWhenUnspecified() {
    val elements = makeSegmentWithTags(
      listOf(tag(simpleTags = listOf(simpleTag("TITLE", "Test")))),
    )
    val result = extractMatroskaTags(elements)
    assertNotNull(result)
    assertNull(result.tags[0].simpleTags[0].default)
  }

  // -- Target UIDs ----------------------------------------------------------

  /** Create a leaf element with an 8-byte big-endian unsigned integer. */
  private fun uint64Element(id: Long, value: Long) = EbmlElement(
    id = dev.transmute.model.identify.EbmlId(id),
    data = Bytes(
      byteArrayOf(
        ((value shr 56) and 0xFF).toByte(),
        ((value shr 48) and 0xFF).toByte(),
        ((value shr 40) and 0xFF).toByte(),
        ((value shr 32) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
      ),
    ),
  )

  @Test
  fun extractsTargetTrackUIDs() {
    val targetChildren = mutableListOf<EbmlElement>(
      uintElement(MatroskaIds.TargetTypeValue.value, 30),
      uint64Element(MatroskaIds.TagTrackUID.value, 12345L),
      uint64Element(MatroskaIds.TagTrackUID.value, 67890L),
    )
    val targetsEl = master(MatroskaIds.Targets.value, targetChildren)
    val tagEl = master(
      MatroskaIds.Tag.value,
      listOf(targetsEl, simpleTag("TITLE", "Track Tag")),
    )
    val elements = makeSegmentWithTags(listOf(tagEl))
    val result = extractMatroskaTags(elements)
    assertNotNull(result)
    val targets = result.tags[0].targets
    assertNotNull(targets)
    assertEquals(listOf(12345uL, 67890uL), targets.trackUIDs)
    assertTrue(targets.editionUIDs.isEmpty())
    assertTrue(targets.chapterUIDs.isEmpty())
    assertTrue(targets.attachmentUIDs.isEmpty())
  }

  @Test
  fun extractsTargetEditionAndChapterUIDs() {
    val targetChildren = listOf(
      uint64Element(MatroskaIds.TagEditionUID.value, 111L),
      uint64Element(MatroskaIds.TagChapterUID.value, 222L),
      uint64Element(MatroskaIds.TagAttachmentUID.value, 333L),
    )
    val targetsEl = master(MatroskaIds.Targets.value, targetChildren)
    val tagEl = master(
      MatroskaIds.Tag.value,
      listOf(targetsEl, simpleTag("TITLE", "Edition Tag")),
    )
    val elements = makeSegmentWithTags(listOf(tagEl))
    val result = extractMatroskaTags(elements)
    assertNotNull(result)
    val targets = result.tags[0].targets
    assertNotNull(targets)
    assertEquals(listOf(111uL), targets.editionUIDs)
    assertEquals(listOf(222uL), targets.chapterUIDs)
    assertEquals(listOf(333uL), targets.attachmentUIDs)
    assertTrue(targets.trackUIDs.isEmpty())
  }

  @Test
  fun targetUIDsDefaultToEmptyLists() {
    val elements = makeSegmentWithTags(
      listOf(tag(simpleTags = listOf(simpleTag("TITLE", "No UIDs")))),
    )
    val result = extractMatroskaTags(elements)
    assertNotNull(result)
    assertNull(result.tags[0].targets)
  }
}
