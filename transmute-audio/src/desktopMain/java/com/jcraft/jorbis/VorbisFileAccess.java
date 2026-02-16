package com.jcraft.jorbis;

/**
 * Internal shim to access JOrbis' package-private VorbisFile#read API.
 */
public final class VorbisFileAccess {
  private VorbisFileAccess() {}

  public static int read(
      VorbisFile file,
      byte[] buffer,
      int offset,
      int length,
      int bigEndian,
      int wordSize,
      int[] bitstream
  ) {
    return file.read(buffer, offset, length, bigEndian, wordSize, bitstream);
  }
}
