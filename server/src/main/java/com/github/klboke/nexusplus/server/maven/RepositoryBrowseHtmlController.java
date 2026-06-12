package com.github.klboke.nexusplus.server.maven;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RepositoryBrowseHtmlController {
  private final MavenHtmlListingService htmlListing;

  public RepositoryBrowseHtmlController(MavenHtmlListingService htmlListing) {
    this.htmlListing = htmlListing;
  }

  @GetMapping({
      "/service/rest/repository/browse/{repository}",
      "/service/rest/repository/browse/{repository}/**"
  })
  public ResponseEntity<String> browse(
      @PathVariable("repository") String repository,
      HttpServletRequest request) {
    String path = extractPath(repository, request);
    boolean trailingSlash = request.getRequestURI().endsWith("/");
    if (!path.isEmpty() && !trailingSlash && htmlListing.renderBrowse(repository, path).isEmpty()) {
      HttpHeaders headers = new HttpHeaders();
      headers.setLocation(URI.create(request.getRequestURL().append("/").toString()));
      return ResponseEntity.status(HttpStatus.SEE_OTHER).headers(headers).build();
    }
    return htmlListing.renderBrowse(repository, path)
        .map(html -> {
          byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
          HttpHeaders headers = new HttpHeaders();
          headers.setContentType(MediaType.TEXT_HTML);
          headers.setContentLength(bytes.length);
          return ResponseEntity.ok().headers(headers).body(html);
        })
        .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
  }

  private static String extractPath(String repository, HttpServletRequest request) {
    String uri = request.getRequestURI();
    String base = request.getContextPath() + "/service/rest/repository/browse/" + repository;
    if (uri.equals(base)) {
      return "";
    }
    String prefix = base + "/";
    if (!uri.startsWith(prefix)) {
      return "";
    }
    return uri.substring(prefix.length());
  }
}
