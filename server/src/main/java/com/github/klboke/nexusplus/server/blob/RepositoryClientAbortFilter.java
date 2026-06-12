package com.github.klboke.nexusplus.server.blob;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(RepositoryClientAbortFilter.FILTER_ORDER)
public final class RepositoryClientAbortFilter extends OncePerRequestFilter {
  static final int FILTER_ORDER = SessionRepositoryFilter.DEFAULT_ORDER + 21;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    try {
      filterChain.doFilter(request, response);
    } catch (IOException e) {
      if (handle(request, e)) {
        return;
      }
      throw e;
    } catch (ServletException e) {
      if (handle(request, e)) {
        return;
      }
      throw e;
    } catch (RuntimeException e) {
      if (handle(request, e)) {
        return;
      }
      throw e;
    }
  }

  @Override
  protected boolean shouldNotFilterAsyncDispatch() {
    return false;
  }

  @Override
  protected boolean shouldNotFilterErrorDispatch() {
    return false;
  }

  private static boolean handle(HttpServletRequest request, Throwable exception) {
    if (!RepositoryClientAbortSupport.shouldHandle(request, exception)) {
      return false;
    }
    TempBlobFiles.logClientAbort(request, exception);
    return true;
  }
}
