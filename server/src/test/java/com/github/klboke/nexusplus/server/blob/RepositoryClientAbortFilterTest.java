package com.github.klboke.nexusplus.server.blob;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

class RepositoryClientAbortFilterTest {
  private final RepositoryClientAbortFilter filter = new RepositoryClientAbortFilter();

  @Test
  void suppressesRepositoryClientAbortFromFilterChain() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/repository/maven-public/com/acme/app.jar");
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertDoesNotThrow(() -> filter.doFilter(request, response, failingChain(new IOException("Broken pipe"))));

    assertTrue(TempBlobFiles.hasHandledClientAbort(request));
  }

  @Test
  void suppressesFollowUpExceptionAfterCopyHandledClientAbort() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/repository/maven-public/com/acme/app.jar");
    request.setAttribute(TempBlobFiles.CLIENT_ABORT_HANDLED_ATTR, Boolean.TRUE);
    MockHttpServletResponse response = new MockHttpServletResponse();
    ServletException exception =
        new ServletException(new AsyncRequestNotUsableException("Response not usable after response errors"));

    assertDoesNotThrow(() -> filter.doFilter(request, response, failingChain(exception)));
  }

  @Test
  void propagatesNonRepositoryClientAbort() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/status");
    MockHttpServletResponse response = new MockHttpServletResponse();
    IOException exception = new IOException("Broken pipe");

    IOException thrown = assertThrows(IOException.class,
        () -> filter.doFilter(request, response, failingChain(exception)));

    assertSame(exception, thrown);
  }

  @Test
  void propagatesRepositoryNonClientAbortWithoutHandledMarker() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/repository/maven-public/com/acme/app.jar");
    MockHttpServletResponse response = new MockHttpServletResponse();
    IOException exception = new IOException("disk failed");

    IOException thrown = assertThrows(IOException.class,
        () -> filter.doFilter(request, response, failingChain(exception)));

    assertSame(exception, thrown);
  }

  private static FilterChain failingChain(Exception exception) {
    return (request, response) -> {
      if (exception instanceof IOException ioException) {
        throw ioException;
      }
      if (exception instanceof ServletException servletException) {
        throw servletException;
      }
      throw new IllegalArgumentException(exception);
    };
  }
}
