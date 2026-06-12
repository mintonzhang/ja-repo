package com.github.klboke.nexusplus.server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SecurityHeadersFilter extends OncePerRequestFilter {
  private final boolean hstsEnabled;

  public SecurityHeadersFilter(
      @Value("${nexus-plus.security.hsts-enabled:false}") boolean hstsEnabled) {
    this.hstsEnabled = hstsEnabled;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("X-Frame-Options", "DENY");
    response.setHeader("Referrer-Policy", "no-referrer");
    response.setHeader("Content-Security-Policy", "frame-ancestors 'none'; object-src 'none'; base-uri 'self'");
    if (hstsEnabled) {
      response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
    }
    filterChain.doFilter(request, response);
  }
}
