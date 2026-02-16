package dev.transmute.core

/** Controls whether metadata (EXIF, XMP, GPS, etc.) is kept or stripped during conversion. */
enum class MetadataPolicy {
  PRESERVE,
  STRIP_ALL,
}
