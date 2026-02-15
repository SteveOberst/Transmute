package dev.transmute.image

import dev.transmute.core.MetadataPolicy
import dev.transmute.image.ImageTestHelpers.solidColor
import dev.transmute.image.ImageTestHelpers.testContext
import dev.transmute.core.pipeline.TransformId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ImageMetadataTransformTest {

  private val fakeExif = byteArrayOf(0x45, 0x78, 0x69, 0x66) // "Exif"
  private val fakeXmp = byteArrayOf(0x3C, 0x78, 0x3A) // "<x:"

  private fun imageWithMetadata() = solidColor(10, 10, 128, 128, 128).copy(
    metadata = ImageMetadata(
      exifBlob = fakeExif,
      xmpBlob = fakeXmp,
      appMetadata = mapOf("source" to "camera", "lens" to "50mm"),
    ),
  )

  @Test
  fun preservePolicyKeepsAllMetadata() = runTest {
    val input = imageWithMetadata()
    val transform = ImageMetadataTransform(MetadataPolicy.PRESERVE)
    val result = transform.apply(input, testContext()) as ImageIR

    assertSame(input, result, "PRESERVE should return the same instance")
  }

  @Test
  fun stripAllRemovesExif() = runTest {
    val input = imageWithMetadata()
    val transform = ImageMetadataTransform(MetadataPolicy.STRIP_ALL)
    val result = transform.apply(input, testContext()) as ImageIR

    assertNull(result.metadata.exifBlob, "EXIF should be stripped")
    assertNull(result.metadata.xmpBlob, "XMP should be stripped")
    assertEquals(emptyMap(), result.metadata.appMetadata, "App metadata should be cleared")
  }

  @Test
  fun stripAllPreservesPixels() = runTest {
    val input = imageWithMetadata()
    val transform = ImageMetadataTransform(MetadataPolicy.STRIP_ALL)
    val result = transform.apply(input, testContext()) as ImageIR

    assertEquals(input.width, result.width)
    assertEquals(input.height, result.height)
    assertEquals(input.buffer, result.buffer, "Pixel data should be unchanged")
    assertEquals(input.pixelFormat, result.pixelFormat)
  }

  @Test
  fun nonImageIRPassedThrough() = runTest {
    val transform = ImageMetadataTransform(MetadataPolicy.STRIP_ALL)
    val input = "not an image"
    val result = transform.apply(input, testContext())
    assertSame(input, result)
  }

  @Test
  fun transformId() {
    val transform = ImageMetadataTransform(MetadataPolicy.PRESERVE)
    assertEquals(TransformId("image-metadata"), transform.id)
  }
}
