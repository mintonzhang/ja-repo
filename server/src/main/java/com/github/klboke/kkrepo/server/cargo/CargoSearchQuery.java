package com.github.klboke.kkrepo.server.cargo;

public record CargoSearchQuery(String query, int perPage, int page) {
  public static final int DEFAULT_PER_PAGE = 10;
  public static final int MAX_PER_PAGE = 100;
  private static final int MAX_LOCAL_SCAN = 300;

  public CargoSearchQuery {
    query = query == null ? "" : query.trim();
    perPage = Math.max(1, Math.min(perPage, MAX_PER_PAGE));
    page = Math.max(1, page);
  }

  int offset() {
    return (page - 1) * perPage;
  }

  int localScanLimit() {
    long requested = (long) Math.max(1, page) * perPage * 4L;
    return (int) Math.max(perPage, Math.min(MAX_LOCAL_SCAN, requested));
  }
}
