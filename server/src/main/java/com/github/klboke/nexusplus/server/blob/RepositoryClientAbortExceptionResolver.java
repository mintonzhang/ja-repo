package com.github.klboke.nexusplus.server.blob;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class RepositoryClientAbortExceptionResolver implements HandlerExceptionResolver {
  @Override
  public ModelAndView resolveException(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      Exception exception) {
    if (!RepositoryClientAbortSupport.shouldHandle(request, exception)) {
      return null;
    }
    TempBlobFiles.logClientAbort(request, exception);
    return new ModelAndView();
  }
}
