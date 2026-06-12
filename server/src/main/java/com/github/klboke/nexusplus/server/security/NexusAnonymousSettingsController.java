package com.github.klboke.nexusplus.server.security;

import com.github.klboke.nexusplus.server.security.NexusSecurityPayloads.NexusAnonymousSettings;
import com.github.klboke.nexusplus.server.security.SecurityPayloads.AnonymousSettingsCommand;
import com.github.klboke.nexusplus.server.security.SecurityPayloads.AnonymousSettingsView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/service/rest/internal/ui/anonymous-settings")
public class NexusAnonymousSettingsController {
  private final SecurityManagementService securityService;

  public NexusAnonymousSettingsController(SecurityManagementService securityService) {
    this.securityService = securityService;
  }

  @GetMapping
  public NexusAnonymousSettings read() {
    AnonymousSettingsView settings = securityService.anonymousSettings();
    return new NexusAnonymousSettings(
        settings.enabled(),
        settings.userId(),
        settings.realmName());
  }

  @PutMapping
  public ResponseEntity<Void> update(@RequestBody NexusAnonymousSettings request) {
    securityService.saveAnonymousSettings(new AnonymousSettingsCommand(
        request.enabled(),
        null,
        request.userId(),
        request.realmName()));
    return ResponseEntity.noContent().build();
  }
}
