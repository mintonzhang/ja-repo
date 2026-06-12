package com.github.klboke.nexusplus.server.security;

import com.github.klboke.nexusplus.auth.AccessDecision;
import com.github.klboke.nexusplus.auth.AccessDecisionService;
import com.github.klboke.nexusplus.auth.PermissionAction;
import com.github.klboke.nexusplus.auth.RepositoryPermission;
import com.github.klboke.nexusplus.core.RepositoryFormat;
import com.github.klboke.nexusplus.persistence.mysql.dao.RepositoryDao;
import com.github.klboke.nexusplus.persistence.mysql.model.RepositoryRecord;
import com.github.klboke.nexusplus.server.npm.NpmTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(RepositorySecurityFilter.FILTER_ORDER)
public class RepositorySecurityFilter extends OncePerRequestFilter {
  static final int FILTER_ORDER = SessionRepositoryFilter.DEFAULT_ORDER + 20;
  public static final String REPOSITORY_RECORD_ATTRIBUTE =
      RepositorySecurityFilter.class.getName() + ".REPOSITORY_RECORD";
  private final SecurityAuthenticationService authenticationService;
  private final AccessDecisionService accessDecisionService;
  private final RepositoryDao repositoryDao;
  private final boolean anonymousReadEnabled;

  public RepositorySecurityFilter(
      SecurityAuthenticationService authenticationService,
      AccessDecisionService accessDecisionService,
      RepositoryDao repositoryDao,
      @Value("${nexus-plus.security.anonymous-read-enabled:false}") boolean anonymousReadEnabled) {
    this.authenticationService = authenticationService;
    this.accessDecisionService = accessDecisionService;
    this.repositoryDao = repositoryDao;
    this.anonymousReadEnabled = anonymousReadEnabled;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    Optional<RepositoryRequest> securedRequest = resolve(request);
    if (securedRequest.isEmpty()) {
      filterChain.doFilter(request, response);
      return;
    }

    RepositoryRequest target = securedRequest.get();
    Optional<RepositoryRecord> repository = repositoryDao.findByName(target.repository());
    if (repository.isEmpty()) {
      filterChain.doFilter(request, response);
      return;
    }
    request.setAttribute(REPOSITORY_RECORD_ATTRIBUTE, repository.get());
    if (target.npmTokenRoute() && repository.get().format() == RepositoryFormat.NPM) {
      filterChain.doFilter(request, response);
      return;
    }

    Optional<AuthenticatedSubject> authenticated = authenticationService.authenticate(request);
    if (authenticated.isEmpty()) {
      authenticated = target.readOnly()
          ? authenticationService.authenticateAnonymous(anonymousReadEnabled)
          : Optional.empty();
      if (authenticated.isEmpty()) {
        challenge(response);
        return;
      }
    }
    request.setAttribute(AuthenticatedSubject.REQUEST_ATTRIBUTE, authenticated.get());

    AccessDecision decision = decide(authenticated.get(), repository.get(), target);
    if (!decision.allowed()) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN, decision.reason());
      return;
    }
    filterChain.doFilter(request, response);
  }

  private AccessDecision decide(
      AuthenticatedSubject subject,
      RepositoryRecord repository,
      RepositoryRequest target) {
    AccessDecision lastDenied = AccessDecision.deny("missing permission");
    for (PermissionAction action : target.actions()) {
      AccessDecision decision = accessDecisionService.decide(
          subject.permissionSubject(),
          new RepositoryPermission(repository.name(), repository.format(), target.path(), action));
      if (decision.allowed()) {
        return decision;
      }
      lastDenied = decision;
    }
    return lastDenied;
  }

  private Optional<RepositoryRequest> resolve(HttpServletRequest request) {
    String method = request.getMethod();
    String uri = stripContextPath(request);
    if (uri.startsWith("/repository/")) {
      return repositoryRoute(method, uri);
    }
    if (uri.startsWith("/service/rest/repository/browse/")) {
      return browseRoute(method, uri);
    }
    if ("POST".equalsIgnoreCase(method) && uri.equals("/service/rest/v1/components")) {
      String repository = request.getParameter("repository");
      if (repository == null || repository.isBlank()) {
        return Optional.empty();
      }
      return Optional.of(new RepositoryRequest(repository.trim(), "", List.of(PermissionAction.EDIT), false));
    }
    if (uri.startsWith("/service/rest/internal/ui/upload/")) {
      String repository = uri.substring("/service/rest/internal/ui/upload/".length());
      int slash = repository.indexOf('/');
      if (slash >= 0) {
        repository = repository.substring(0, slash);
      }
      if (repository.isBlank()) {
        return Optional.empty();
      }
      return Optional.of(new RepositoryRequest(decode(repository), "", List.of(PermissionAction.EDIT), false));
    }
    return Optional.empty();
  }

  private Optional<RepositoryRequest> repositoryRoute(String method, String uri) {
    String remaining = uri.substring("/repository/".length());
    if (remaining.isBlank()) {
      return Optional.empty();
    }
    int slash = remaining.indexOf('/');
    String repository = slash < 0 ? remaining : remaining.substring(0, slash);
    String path = slash < 0 ? "" : remaining.substring(slash + 1);
    if (repository.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(new RepositoryRequest(
        decode(repository),
        path,
        actionsForRepository(method, path),
        isNpmTokenRoute(method, path)));
  }

  private Optional<RepositoryRequest> browseRoute(String method, String uri) {
    if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
      return Optional.empty();
    }
    String remaining = uri.substring("/service/rest/repository/browse/".length());
    if (remaining.isBlank()) {
      return Optional.empty();
    }
    int slash = remaining.indexOf('/');
    String repository = slash < 0 ? remaining : remaining.substring(0, slash);
    String path = slash < 0 ? "" : remaining.substring(slash + 1);
    if (repository.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(new RepositoryRequest(decode(repository), path, List.of(PermissionAction.BROWSE), false));
  }

  private boolean isNpmTokenRoute(String method, String path) {
    String normalizedMethod = method == null ? "" : method.toUpperCase();
    return ("PUT".equals(normalizedMethod) && NpmTokenService.isLoginPath(path))
        || ("DELETE".equals(normalizedMethod) && NpmTokenService.isLogoutPath(path));
  }

  private List<PermissionAction> actionsForRepository(String method, String path) {
    if (isNpmAuditRoute(method, path)) {
      return List.of(PermissionAction.READ, PermissionAction.BROWSE);
    }
    return switch (method.toUpperCase()) {
      case "GET", "HEAD", "OPTIONS", "TRACE" -> List.of(PermissionAction.READ);
      case "POST", "PATCH", "MKCOL" -> List.of(PermissionAction.ADD);
      case "PUT" -> List.of(PermissionAction.EDIT);
      case "DELETE" -> List.of(PermissionAction.DELETE);
      default -> List.of(PermissionAction.ADMIN);
    };
  }

  private boolean isNpmAuditRoute(String method, String path) {
    return "POST".equalsIgnoreCase(method)
        && isNpmAuditPath(path);
  }

  private boolean isNpmAuditPath(String path) {
    return "-/npm/v1/security/audits".equals(path)
        || "-/npm/v1/security/audits/quick".equals(path)
        || "-/npm/v1/security/advisories/bulk".equals(path);
  }

  private String stripContextPath(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String contextPath = request.getContextPath();
    if (contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath)) {
      return uri.substring(contextPath.length());
    }
    return uri;
  }

  private void challenge(HttpServletResponse response) throws IOException {
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"nexus-plus\"");
    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
  }

  private static String decode(String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }

  private record RepositoryRequest(
      String repository,
      String path,
      List<PermissionAction> actions,
      boolean npmTokenRoute) {
    private boolean readOnly() {
      return actions.stream().allMatch(action -> action == PermissionAction.BROWSE || action == PermissionAction.READ);
    }
  }
}
