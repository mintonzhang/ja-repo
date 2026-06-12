package com.github.klboke.nexusplus.server.blob;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.RequestDispatcher;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.ModelAndView;

class RepositoryClientAbortExceptionResolverTest {
  private final RepositoryClientAbortExceptionResolver resolver = new RepositoryClientAbortExceptionResolver();

  @Test
  void resolvesRepositoryAsyncClientAbortWithoutWritingResponse() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/repository/maven-public/com/acme/app.jar");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AsyncRequestNotUsableException exception = new AsyncRequestNotUsableException(
        "ServletOutputStream failed to write", new IOException("Broken pipe"));

    ModelAndView result = resolver.resolveException(request, response, null, exception);

    assertNotNull(result);
    assertTrue(result.isEmpty());
    assertTrue(TempBlobFiles.hasHandledClientAbort(request));
    assertFalse(response.isCommitted());
  }

  @Test
  void resolvesFollowUpExceptionAfterCopyHandledClientAbort() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/repository/npm-public/@scope/pkg/-/pkg-1.0.0.tgz");
    request.setAttribute(TempBlobFiles.CLIENT_ABORT_HANDLED_ATTR, Boolean.TRUE);
    MockHttpServletResponse response = new MockHttpServletResponse();
    AsyncRequestNotUsableException exception =
        new AsyncRequestNotUsableException("Response not usable after response errors");

    ModelAndView result = resolver.resolveException(request, response, null, exception);

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void resolvesErrorDispatchUsingOriginalRepositoryUri() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
    request.setContextPath("/nexus");
    request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/nexus/repository/raw/file.txt");
    MockHttpServletResponse response = new MockHttpServletResponse();
    IOException exception = new IOException("Connection reset by peer");

    ModelAndView result = resolver.resolveException(request, response, null, exception);

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void ignoresNonRepositoryClientAbort() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/status");
    MockHttpServletResponse response = new MockHttpServletResponse();

    ModelAndView result = resolver.resolveException(request, response, null, new IOException("Broken pipe"));

    assertNull(result);
  }

  @Test
  void ignoresRepositoryNonClientAbortWithoutHandledMarker() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/repository/maven-public/com/acme/app.jar");
    MockHttpServletResponse response = new MockHttpServletResponse();

    ModelAndView result = resolver.resolveException(request, response, null, new IOException("disk failed"));

    assertNull(result);
  }
}
