package com.github.klboke.nexusplus.server;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalStatusController {
  @GetMapping("/internal/status")
  public Map<String, String> status() {
    return Map.of("service", "nexus-plus", "status", "starting");
  }
}
