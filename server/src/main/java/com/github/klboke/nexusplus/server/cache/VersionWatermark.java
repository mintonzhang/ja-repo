package com.github.klboke.nexusplus.server.cache;

import java.util.Map;

public interface VersionWatermark {
  long bump(String name);

  long current(String name);

  Map<String, Long> currentAll();
}
