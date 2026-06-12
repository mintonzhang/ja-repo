package com.github.klboke.nexusplus.browseui;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class BrowseUiController {
  private static final Resource BROWSE_INDEX =
      new ClassPathResource("META-INF/resources/browse/index.html");

  @GetMapping({"/browse", "/browse/", "/browse/index.html"})
  public ResponseEntity<Resource> browse() {
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_HTML)
        .cacheControl(CacheControl.noCache())
        .body(BROWSE_INDEX);
  }
}
