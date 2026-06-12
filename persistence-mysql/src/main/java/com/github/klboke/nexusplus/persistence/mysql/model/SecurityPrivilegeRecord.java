package com.github.klboke.nexusplus.persistence.mysql.model;

import java.util.Map;

public record SecurityPrivilegeRecord(
    String privilegeId,
    String name,
    String description,
    String type,
    boolean readOnly,
    Map<String, Object> properties) {
}
