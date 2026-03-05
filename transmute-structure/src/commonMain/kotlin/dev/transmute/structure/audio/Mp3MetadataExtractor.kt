@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.metadata.id3.*
import dev.transmute.model.structure.audio.types.Mp3Raw
import dev.transmute.model.structure.audio.types.id3v1Tag
import dev.transmute.structure.common.parseId3v2FromBytes

// -- ID3v1 genre table (Winamp extended, 0-191) ------------------------------

private val ID3V1_GENRES: Array<String> = arrayOf(
  "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge",
  "Hip-Hop", "Jazz", "Metal", "New Age", "Oldies", "Other", "Pop", "R&B",
  "Rap", "Reggae", "Rock", "Techno", "Industrial", "Alternative", "Ska",
  "Death Metal", "Pranks", "Soundtrack", "Euro-Techno", "Ambient",
  "Trip-Hop", "Vocal", "Jazz+Funk", "Fusion", "Trance", "Classical",
  "Instrumental", "Acid", "House", "Game", "Sound Clip", "Gospel", "Noise",
  "AlternRock", "Bass", "Soul", "Punk", "Space", "Meditative",
  "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic", "Darkwave",
  "Techno-Industrial", "Electronic", "Pop-Folk", "Eurodance", "Dream",
  "Southern Rock", "Comedy", "Cult", "Gangsta", "Top 40", "Christian Rap",
  "Pop/Funk", "Jungle", "Native American", "Cabaret", "New Wave",
  "Psychedelic", "Rave", "Showtunes", "Trailer", "Lo-Fi", "Tribal",
  "Acid Punk", "Acid Jazz", "Polka", "Retro", "Musical", "Rock & Roll",
  "Hard Rock",
  // Winamp extensions (80-191)
  "Folk", "Folk-Rock", "National Folk", "Swing", "Fast Fusion", "Bebob",
  "Latin", "Revival", "Celtic", "Bluegrass", "Avantgarde", "Gothic Rock",
  "Progressive Rock", "Psychedelic Rock", "Symphonic Rock", "Slow Rock",
  "Big Band", "Chorus", "Easy Listening", "Acoustic", "Humour", "Speech",
  "Chanson", "Opera", "Chamber Music", "Sonata", "Symphony",
  "Booty Bass", "Primus", "Porn Groove", "Satire", "Slow Jam", "Club",
  "Tango", "Samba", "Folklore", "Ballad", "Power Ballad", "Rhythmic Soul",
  "Freestyle", "Duet", "Punk Rock", "Drum Solo", "A capella", "Euro-House",
  "Dance Hall", "Goa", "Drum & Bass", "Club-House", "Hardcore", "Terror",
  "Indie", "BritPop", "Negerpunk", "Polsk Punk", "Beat",
  "Christian Gangsta Rap", "Heavy Metal", "Black Metal", "Crossover",
  "Contemporary Christian", "Christian Rock", "Merengue", "Salsa",
  "Thrash Metal", "Anime", "Jpop", "Synthpop", "Abstract", "Art Rock",
  "Baroque", "Bhangra", "Big Beat", "Breakbeat", "Chillout", "Downtempo",
  "Dub", "EBM", "Eclectic", "Electro", "Electroclash", "Emo",
  "Experimental", "Garage", "Global", "IDM", "Illbient", "Industro-Goth",
  "Jam Band", "Krautrock", "Leftfield", "Lounge", "Math Rock",
  "New Romantic", "Nu-Breakz", "Post-Punk", "Post-Rock", "Psytrance",
  "Shoegaze", "Space Rock", "Trop Rock", "World Music", "Neoclassical",
  "Audiobook", "Audio Theatre", "Neue Deutsche Welle", "Podcast",
  "Indie Rock", "G-Funk", "Dubstep", "Garage Rock", "Psybient",
)

/**
 * Extract metadata from a parsed [Mp3Raw].
 *
 * Supports:
 * - **ID3v2** - full tag parse from raw header bytes
 * - **ID3v1** - converted from the existing typed accessor
 */
fun Mp3Raw.extractMetadata(): List<MediaMetadata> = buildList {
  extractId3v2()?.let(::add)
  extractId3v1()?.let(::add)
}

// -- ID3v2 extraction ---------------------------------------------------------

private fun Mp3Raw.extractId3v2(): Id3v2Metadata? {
  val tag = id3v2Tag ?: return null
  return parseId3v2FromBytes(tag.data)
}

// -- ID3v1 extraction ---------------------------------------------------------

private fun Mp3Raw.extractId3v1(): Id3v1Metadata? {
  val tag = id3v1Tag ?: return null
  val genreName = if (tag.genre in ID3V1_GENRES.indices) ID3V1_GENRES[tag.genre] else null
  return Id3v1Metadata(
    title = tag.title,
    artist = tag.artist,
    album = tag.album,
    year = tag.year,
    comment = tag.comment,
    track = tag.track?.toUByte(),
    genre = tag.genre.toUByte(),
    genreName = genreName,
    original = dev.transmute.model.metadata.common.PayloadRef(sizeBytes = 128uL),
  )
}
