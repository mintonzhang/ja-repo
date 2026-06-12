package com.github.klboke.nexusplus.auth;

public interface AccessDecisionService {
  AccessDecision decide(PermissionSubject subject, RepositoryPermission permission);
}
