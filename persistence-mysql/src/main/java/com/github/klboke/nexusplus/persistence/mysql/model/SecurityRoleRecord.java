package com.github.klboke.nexusplus.persistence.mysql.model;

import java.util.Map;

public record SecurityRoleRecord(
    String roleId,
    String source,
    String name,
    String description,
    boolean readOnly,
    Map<String, Object> attributes) {
}
