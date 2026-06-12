package com.github.klboke.nexusplus.core;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

/**
 * Optional fast-path for blob streams backed by a local file region. Servlet writers can use this
 * to delegate transfer to {@code FileChannel.transferTo} instead of copying through a heap buffer.
 */
public interface BlobFileRegion {
  Path path();

  long position();

  long count();

  void transferFileRegionTo(OutputStream out) throws IOException;
}
