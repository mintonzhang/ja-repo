package com.github.klboke.kkrepo.server.docker;

import com.github.klboke.kkrepo.protocol.docker.DockerConstants;
import com.github.klboke.kkrepo.server.docker.DockerTransferLimiter.TransferKind;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.core.annotation.Order;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(DockerTransferLimitFilter.FILTER_ORDER)
public class DockerTransferLimitFilter extends OncePerRequestFilter {
  static final int FILTER_ORDER = SessionRepositoryFilter.DEFAULT_ORDER + 16;
  private static final int TOO_MANY_REQUESTS = 429;

  private final DockerTransferLimiter limiter;

  public DockerTransferLimitFilter(DockerTransferLimiter limiter) {
    this.limiter = limiter;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    TransferKind kind = transferKind(request);
    if (kind == null) {
      filterChain.doFilter(request, response);
      return;
    }
    try (DockerTransferLimiter.Lease lease = limiter.tryAcquire(kind)) {
      if (!lease.acquired()) {
        response.setHeader(DockerConstants.API_VERSION_HEADER, DockerConstants.API_VERSION);
        response.setHeader("Retry-After", "5");
        response.sendError(TOO_MANY_REQUESTS, "Docker transfer concurrency limit exceeded");
        return;
      }
      filterChain.doFilter(request, response);
    }
  }

  private static TransferKind transferKind(HttpServletRequest request) {
    String uri = stripContextPath(request);
    if (!uri.equals("/v2") && !uri.equals("/v2/") && !uri.startsWith("/v2/")) {
      return null;
    }
    String method = request.getMethod() == null ? "" : request.getMethod().toUpperCase(Locale.ROOT);
    if (("POST".equals(method) || "PATCH".equals(method) || "PUT".equals(method))
        && (uri.contains("/blobs/uploads/") || uri.endsWith("/blobs/uploads") || uri.endsWith("/blobs/uploads/"))) {
      return TransferKind.UPLOAD;
    }
    if ("GET".equals(method) && uri.contains("/blobs/") && !uri.contains("/blobs/uploads/")) {
      return TransferKind.DOWNLOAD;
    }
    return null;
  }

  private static String stripContextPath(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String contextPath = request.getContextPath();
    if (contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath)) {
      return uri.substring(contextPath.length());
    }
    return uri;
  }
}
