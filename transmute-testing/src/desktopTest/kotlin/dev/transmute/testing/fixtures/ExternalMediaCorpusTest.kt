package dev.transmute.testing.fixtures

import java.nio.file.Files
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExternalMediaCorpusTest {

  @Test
  fun loadParsesManifestAndResolvesFiles() {
    val root = Files.createTempDirectory("transmute-fixture-corpus")
    try {
      val imageDir = Files.createDirectories(root.resolve("images"))
      val videoDir = Files.createDirectories(root.resolve("videos"))
      imageDir.resolve("still.png").writeBytes(byteArrayOf(1, 2, 3))
      videoDir.resolve("clip.mov").writeBytes(byteArrayOf(4, 5, 6))
      root.resolve(ExternalMediaCorpus.defaultManifestName).writeText(
        """
        # id	relativePath	domain	format	tags	notes	expectations
        still-png	images/still.png	image	png	Smoke,Metadata	Small still image	detect=png;domain=image;metadata.min=1
        sample-mov	videos/clip.mov	video	mov	transcode	Short reference clip
        """.trimIndent(),
      )

      val fixtures = ExternalMediaCorpus.load(root.toFile())

      assertEquals(2, fixtures.size)
      assertEquals("still-png", fixtures[0].id)
      assertEquals(ExternalMediaDomain.IMAGE, fixtures[0].domain)
      assertEquals(setOf("smoke", "metadata"), fixtures[0].tags)
      assertEquals("png", fixtures[0].expectations.detectedFormat)
      assertEquals(ExternalMediaDomain.IMAGE, fixtures[0].expectations.domain)
      assertEquals(1, fixtures[0].expectations.metadataMin)
      assertTrue(fixtures[0].file.isFile)
      assertEquals("sample-mov", fixtures[1].id)
      assertEquals(ExternalMediaDomain.VIDEO, fixtures[1].domain)
      assertEquals("Short reference clip", fixtures[1].notes)
      assertTrue(fixtures[1].expectations.isEmpty)
    } finally {
      root.toFile().deleteRecursively()
    }
  }

  @Test
  fun loadRejectsUnknownExpectationKeys() {
    val root = Files.createTempDirectory("transmute-fixture-invalid")
    try {
      root.resolve("audio.wav").writeBytes(byteArrayOf(1))
      root.resolve(ExternalMediaCorpus.defaultManifestName).writeText(
        "bad\taudio.wav\taudio\twav\t\t\tunexpected=value",
      )

      assertFailsWith<IllegalStateException> {
        ExternalMediaCorpus.load(root.toFile())
      }
    } finally {
      root.toFile().deleteRecursively()
    }
  }

  @Test
  fun loadConfiguredUsesSystemProperty() {
    val root = Files.createTempDirectory("transmute-fixture-configured")
    val previous = System.getProperty(ExternalMediaCorpus.systemPropertyName)
    try {
      root.resolve("audio.wav").writeBytes(byteArrayOf(9, 8, 7))
      root.resolve(ExternalMediaCorpus.defaultManifestName).writeText(
        "audio-wav	audio.wav	audio	wav	regression	",
      )

      System.setProperty(ExternalMediaCorpus.systemPropertyName, root.toString())

      val fixtures = ExternalMediaCorpus.loadConfigured()

      assertEquals(1, fixtures.size)
      assertEquals("audio-wav", fixtures.single().id)
      assertEquals("wav", fixtures.single().format)
    } finally {
      if (previous == null) {
        System.clearProperty(ExternalMediaCorpus.systemPropertyName)
      } else {
        System.setProperty(ExternalMediaCorpus.systemPropertyName, previous)
      }
      root.toFile().deleteRecursively()
    }
  }

  @Test
  fun configuredCorpusLoadsWhenPresent() {
    val configuredRoot = ExternalMediaCorpus.configuredRoot() ?: return
    val fixtures = ExternalMediaCorpus.loadConfigured()

    assertTrue(configuredRoot.isDirectory)
    assertTrue(fixtures.isNotEmpty(), "Configured real-media corpus should expose at least one fixture")
    assertNotNull(fixtures.first().file)
  }
}