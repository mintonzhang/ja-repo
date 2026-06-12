package com.github.klboke.nexusplus.auth;

import com.github.klboke.nexusplus.core.RepositoryFormat;

public record RepositoryPermission(
    String repository,
    RepositoryFormat format,
    String pathPattern,
    PermissionAction action) {
}
