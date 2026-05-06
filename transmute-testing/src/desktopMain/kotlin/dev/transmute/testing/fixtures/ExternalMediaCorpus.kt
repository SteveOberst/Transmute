package dev.transmute.testing.fixtures

import java.io.File

enum class ExternalMediaDomain {
  IMAGE,
  AUDIO,
  VIDEO,
  OTHER,
  ;

  companion object {
    fun parse(token: String): ExternalMediaDomain = when (token.trim().lowercase()) {
      "image" -> IMAGE
      "audio" -> AUDIO
      "video" -> VIDEO
      else -> OTHER
    }
  }
}

data class ExternalMediaFixture(
  val id: String,
  val relativePath: String,
  val domain: ExternalMediaDomain,
  val format: String,
  val tags: Set<String>,
  val notes: String?,
  val expectations: ExternalMediaExpectations,
  val file: File,
)

data class ExternalMediaExpectations(
  val detectedFormat: String? = null,
  val domain: ExternalMediaDomain? = null,
  val structure: Boolean? = null,
  val rawStructure: Boolean? = null,
  val metadataMin: Int? = null,
) {
  val isEmpty: Boolean
    get() = detectedFormat == null && domain == null && structure == null && rawStructure == null && metadataMin == null
}

/**
 * Loads an opt-in corpus of real media fixtures from the local filesystem.
 *
 * The corpus root is discovered from either the JVM system property
 * `transmute.testMediaDir` or the `TRANSMUTE_TEST_MEDIA_DIR` environment
 * variable. The root must contain a tab-separated `fixtures.tsv` manifest with:
 *
 * `id<TAB>relativePath<TAB>domain<TAB>format<TAB>tags<TAB>notes<TAB>expectations`
 *
 * Only the first four columns are required. Blank lines and `#` comments are
 * ignored.
 *
 * Recommended tags:
 * - `smoke` / `detect`: fixture participates in format-detection smoke tests
 * - `structure`: fixture is expected to produce a non-null decoded structure
 * - `metadata`: fixture is expected to produce at least one metadata entry
 *
 * Optional expectation tokens live in the seventh column as `;`-separated
 * `key=value` pairs. Supported keys:
 * - `detect=<format>`
 * - `domain=image|audio|video|other`
 * - `structure=true|false`
 * - `rawStructure=true|false`
 * - `metadata.min=<count>`
 */
object ExternalMediaCorpus {
  const val systemPropertyName: String = "transmute.testMediaDir"
  const val environmentVariableName: String = "TRANSMUTE_TEST_MEDIA_DIR"
  const val defaultManifestName: String = "fixtures.tsv"

  fun configuredRoot(): File? = sequenceOf(
    System.getProperty(systemPropertyName),
    System.getenv(environmentVariableName),
  )
    .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
    .map(::File)
    .firstOrNull()

  fun loadConfigured(manifestName: String = defaultManifestName): List<ExternalMediaFixture> {
    val root = checkNotNull(configuredRoot()) { configurationHint() }
    return load(root, manifestName)
  }

  fun load(root: File, manifestName: String = defaultManifestName): List<ExternalMediaFixture> {
    require(root.isDirectory) {
      "External media corpus root does not exist or is not a directory: ${root.absolutePath}"
    }

    val manifest = File(root, manifestName)
    require(manifest.isFile) {
      "External media corpus manifest not found: ${manifest.absolutePath}. ${manifestFormatHint()}"
    }

    return manifest.readLines().mapIndexedNotNull { index, line ->
      parseLine(root, manifest, index + 1, line)
    }
  }

  fun configurationHint(): String =
    "Set -D$systemPropertyName=<dir> or $environmentVariableName=<dir> and place a $defaultManifestName manifest in that directory."

  fun manifestFormatHint(): String =
    "Expected tab-separated columns: id, relativePath, domain, format, optional tags, optional notes, optional expectations."

  private fun parseLine(
    root: File,
    manifest: File,
    lineNumber: Int,
    line: String,
  ): ExternalMediaFixture? {
    val trimmed = line.trim()
    if (trimmed.isEmpty() || trimmed.startsWith("#")) return null

    val columns = line.split('\t', limit = 7)
    require(columns.size >= 4) {
      "Invalid fixture manifest entry at ${manifest.absolutePath}:$lineNumber. ${manifestFormatHint()}"
    }

    val id = columns[0].trim()
    val relativePath = columns[1].trim()
    val domainToken = columns[2].trim()
    val format = columns[3].trim()
    val tags = columns.getOrNull(4)
      ?.split(',')
      ?.mapNotNull { tag -> tag.trim().lowercase().takeIf(String::isNotEmpty) }
      ?.toSet()
      ?: emptySet()
    val notes = columns.getOrNull(5)?.trim()?.takeIf(String::isNotEmpty)
    val expectations = parseExpectations(manifest, lineNumber, columns.getOrNull(6).orEmpty())

    require(id.isNotEmpty()) {
      "Invalid fixture manifest entry at ${manifest.absolutePath}:$lineNumber. Fixture id must not be blank."
    }
    require(relativePath.isNotEmpty()) {
      "Invalid fixture manifest entry at ${manifest.absolutePath}:$lineNumber. Fixture path must not be blank."
    }
    require(format.isNotEmpty()) {
      "Invalid fixture manifest entry at ${manifest.absolutePath}:$lineNumber. Fixture format must not be blank."
    }

    val file = File(root, relativePath)
    require(file.isFile) {
      "Fixture '$id' points to a missing file: ${file.absolutePath}"
    }

    return ExternalMediaFixture(
      id = id,
      relativePath = relativePath,
      domain = ExternalMediaDomain.parse(domainToken),
      format = format,
      tags = tags,
      notes = notes,
      expectations = expectations,
      file = file,
    )
  }

  private fun parseExpectations(
    manifest: File,
    lineNumber: Int,
    raw: String,
  ): ExternalMediaExpectations {
    if (raw.isBlank()) return ExternalMediaExpectations()

    var detectedFormat: String? = null
    var domain: ExternalMediaDomain? = null
    var structure: Boolean? = null
    var rawStructure: Boolean? = null
    var metadataMin: Int? = null

    raw.split(';')
      .map(String::trim)
      .filter(String::isNotEmpty)
      .forEach { token ->
        val separator = token.indexOf('=')
        require(separator > 0 && separator < token.lastIndex) {
          "Invalid fixture expectation '$token' at ${manifest.absolutePath}:$lineNumber. Use key=value tokens separated by ';'."
        }

        val key = token.substring(0, separator).trim()
        val value = token.substring(separator + 1).trim()

        when (key) {
          "detect" -> detectedFormat = value
          "domain" -> domain = ExternalMediaDomain.parse(value)
          "structure" -> structure = value.parseBooleanExpectation(manifest, lineNumber, key)
          "rawStructure" -> rawStructure = value.parseBooleanExpectation(manifest, lineNumber, key)
          "metadata.min" -> {
            metadataMin = value.toIntOrNull()
            require(metadataMin != null && metadataMin >= 0) {
              "Invalid integer for expectation '$key' at ${manifest.absolutePath}:$lineNumber: '$value'"
            }
          }
          else -> throw IllegalStateException(
            "Unknown fixture expectation '$key' at ${manifest.absolutePath}:$lineNumber. Supported keys: detect, domain, structure, rawStructure, metadata.min.",
          )
        }
      }

    return ExternalMediaExpectations(
      detectedFormat = detectedFormat,
      domain = domain,
      structure = structure,
      rawStructure = rawStructure,
      metadataMin = metadataMin,
    )
  }

  private fun String.parseBooleanExpectation(
    manifest: File,
    lineNumber: Int,
    key: String,
  ): Boolean = when (lowercase()) {
    "true" -> true
    "false" -> false
    else -> throw IllegalStateException(
      "Invalid boolean for expectation '$key' at ${manifest.absolutePath}:$lineNumber: '$this'. Use true or false.",
    )
  }
}