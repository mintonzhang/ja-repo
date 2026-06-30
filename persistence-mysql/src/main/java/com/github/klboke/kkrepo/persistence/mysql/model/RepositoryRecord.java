package com.github.klboke.kkrepo.persistence.mysql.model;

import com.github.klboke.kkrepo.core.RepositoryFormat;
import com.github.klboke.kkrepo.core.RepositoryType;
import java.util.Map;

public record RepositoryRecord(
    Long id,
    String name,
    RepositoryFormat format,
    RepositoryType type,
    String recipeName,
    boolean online,
    Long blobStoreId,
    Long routingRuleId,
    String proxyRemoteUrl,
    String versionPolicy,
    String layoutPolicy,
    String writePolicy,
    boolean strictContentTypeValidation,
    String notes,
    Map<String, Object> attributes) {
}
