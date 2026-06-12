package com.github.klboke.nexusplus.persistence.mysql.model;

import com.github.klboke.nexusplus.core.RepositoryFormat;
import java.time.Instant;
import java.util.Map;

public record ComponentRecord(
    Long id,
    long repositoryId,
    RepositoryFormat format,
    String namespace,
    String name,
    String version,
    String kind,
    byte[] coordinateHash,
    Map<String, Object> attributes,
    Instant lastUpdatedAt) {
}
