package com.github.klboke.nexusplus.persistence.mysql.model;

public record SecurityAnonymousConfigRecord(
    boolean enabled,
    String userSource,
    String userId,
    String realmName) {
}
