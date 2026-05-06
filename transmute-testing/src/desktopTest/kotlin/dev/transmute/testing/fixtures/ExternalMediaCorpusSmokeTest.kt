package dev.transmute.testing.fixtures

import dev.transmute.transmute
import dev.transmute.common.MediaDomain
import dev.transmute.io.asSource
import dev.transmute.model.core.asBytes
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExternalMediaCorpusSmokeTest {

  @Test
  fun configuredSmokeFixturesCanBeDetected() = runTest {
    val configuredRoot = ExternalMediaCorpus.configuredRoot() ?: return@runTest
    val smokeFixtures = ExternalMediaCorpus.loadConfigured()
      .filter { "smoke" in it.tags || "detect" in it.tags }

    if (smokeFixtures.isEmpty()) return@runTest

    val transmute = transmute()
    smokeFixtures.forEach { fixture ->
      val detected = transmute.inspect.detectFormat(fixture.file.readBytes().asBytes().asSource())
      assertEquals(
        normalizeFormatToken(fixture.format),
        normalizeFormatToken(detected.label),
        "Unexpected detected format for fixture '${fixture.id}' under ${configuredRoot.absolutePath}",
      )
    }
  }

  @Test
  fun configuredStructureFixturesProduceStructure() = runTest {
    val structureFixtures = ExternalMediaCorpus.loadConfiguredOrNull("structure") ?: return@runTest
    val transmute = transmute()

    structureFixtures.forEach { fixture ->
      val bytes = fixture.file.readBytes()
      val detected = transmute.inspect.detectFormat(bytes.asBytes().asSource())
      val structure = transmute.inspect.structure(bytes.asBytes().asSource(), detected)
      assertNotNull(structure, "Fixture '${fixture.id}' is tagged 'structure' but produced no structure")
    }
  }

  @Test
  fun configuredMetadataFixturesProduceMetadata() = runTest {
    val metadataFixtures = ExternalMediaCorpus.loadConfiguredOrNull("metadata") ?: return@runTest
    val transmute = transmute()

    metadataFixtures.forEach { fixture ->
      val bytes = fixture.file.readBytes()
      val detected = transmute.inspect.detectFormat(bytes.asBytes().asSource())
      val metadata = transmute.inspect.metadata(bytes.asBytes().asSource(), detected)
      assertTrue(
        metadata.isNotEmpty(),
        "Fixture '${fixture.id}' is tagged 'metadata' but produced no metadata entries",
      )
    }
  }

  @Test
  fun configuredFixturesSatisfyManifestExpectations() = runTest {
    val configuredRoot = ExternalMediaCorpus.configuredRoot() ?: return@runTest
    val fixtures = ExternalMediaCorpus.loadConfigured()
      .filter { !it.expectations.isEmpty }

    if (fixtures.isEmpty()) return@runTest

    val transmute = transmute()
    fixtures.forEach { fixture ->
      val bytes = fixture.file.readBytes()
      val detected = transmute.inspect.detectFormat(bytes.asBytes().asSource())
      val inspection = transmute.inspect.inspect(bytes.asBytes())

      fixture.expectations.detectedFormat?.let { expected ->
        assertEquals(
          normalizeFormatToken(expected),
          normalizeFormatToken(detected.label),
          "Unexpected detected format for fixture '${fixture.id}' under ${configuredRoot.absolutePath}",
        )
      }
      fixture.expectations.domain?.let { expected ->
        assertEquals(
          expected.toMediaDomain(),
          inspection.domain,
          "Unexpected detected domain for fixture '${fixture.id}' under ${configuredRoot.absolutePath}",
        )
      }
      fixture.expectations.structure?.let { expected ->
        assertEquals(
          expected,
          inspection.structure != null,
          "Unexpected structure availability for fixture '${fixture.id}' under ${configuredRoot.absolutePath}",
        )
      }
      fixture.expectations.rawStructure?.let { expected ->
        assertEquals(
          expected,
          inspection.rawStructure != null,
          "Unexpected raw structure availability for fixture '${fixture.id}' under ${configuredRoot.absolutePath}",
        )
      }
      fixture.expectations.metadataMin?.let { minimum ->
        assertTrue(
          inspection.metadata.size >= minimum,
          "Fixture '${fixture.id}' expected at least $minimum metadata entries but produced ${inspection.metadata.size}",
        )
      }
    }
  }

  private fun normalizeFormatToken(value: String): String = when (value.trim().lowercase()) {
    "jpg" -> "jpeg"
    else -> value.trim().lowercase()
  }

  private fun ExternalMediaDomain.toMediaDomain(): MediaDomain = when (this) {
    ExternalMediaDomain.IMAGE -> MediaDomain.IMAGE
    ExternalMediaDomain.AUDIO -> MediaDomain.AUDIO
    ExternalMediaDomain.VIDEO -> MediaDomain.VIDEO
    ExternalMediaDomain.OTHER -> MediaDomain.NONE
  }

  private fun ExternalMediaCorpus.loadConfiguredOrNull(vararg requiredTags: String): List<ExternalMediaFixture>? {
    configuredRoot() ?: return null
    val fixtures = loadConfigured().filter { fixture -> requiredTags.any(fixture.tags::contains) }
    return fixtures.takeIf { it.isNotEmpty() }
  }
}