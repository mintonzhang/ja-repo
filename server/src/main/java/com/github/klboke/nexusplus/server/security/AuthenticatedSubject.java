package com.github.klboke.nexusplus.server.security;

import com.github.klboke.nexusplus.auth.PermissionSubject;
import java.io.Serializable;

public record AuthenticatedSubject(
    String source,
    String userId,
    String realmId,
    Long apiKeyId,
    PermissionSubject permissionSubject) implements Serializable {
  public static final String REQUEST_ATTRIBUTE = AuthenticatedSubject.class.getName();
  public static final String SESSION_ATTRIBUTE = AuthenticatedSubject.class.getName() + ".SESSION";
}
