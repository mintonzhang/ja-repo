package com.github.klboke.nexusplus.core;

import java.io.InputStream;

/**
 * Optional capability for blob streams that can reopen a bounded byte range without reading from
 * the beginning of the object.
 */
public interface BlobRangeReader {
  InputStream openRange(long start, long length);
}
