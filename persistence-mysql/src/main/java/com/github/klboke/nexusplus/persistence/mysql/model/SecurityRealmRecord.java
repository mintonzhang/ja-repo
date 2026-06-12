package com.github.klboke.nexusplus.persistence.mysql.model;

import java.util.Map;

public record SecurityRealmRecord(
    Long id,
    String realmId,
    String type,
    String name,
    boolean enabled,
    int priority,
    Map<String, Object> attributes) {
}
