package com.github.klboke.kkrepo.server.cargo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CargoSearchQueryTest {

  @Test
  void offsetClampsInsteadOfOverflowing() {
    CargoSearchQuery query = new CargoSearchQuery("demo", 100, Integer.MAX_VALUE);

    assertEquals(Integer.MAX_VALUE, query.offset());
  }
}
