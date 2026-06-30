package com.github.klboke.kkrepo.server.settings;

import com.github.klboke.kkrepo.persistence.mysql.dao.UiSettingsDao;
import com.github.klboke.kkrepo.persistence.mysql.model.UiSettingsRecord;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/ui-settings")
public class UiSettingsController {
  private static final List<String> SUPPORTED_DEFAULT_LANGUAGES = List.of(
      UiSettingsDao.LANGUAGE_BROWSER,
      UiSettingsDao.LANGUAGE_ZH_CN,
      UiSettingsDao.LANGUAGE_EN);

  private final UiSettingsDao uiSettingsDao;
  private final String productName;
  private final String productSubtitle;
  private final String logoText;
  private final String logoUrl;
  private final String faviconUrl;

  public UiSettingsController(
      UiSettingsDao uiSettingsDao,
      @Value("${kkrepo.ui.product-name:kkrepo}") String productName,
      @Value("${kkrepo.ui.product-subtitle:Repository Manager}") String productSubtitle,
      @Value("${kkrepo.ui.logo-text:KL}") String logoText,
      @Value("${kkrepo.ui.logo-url:}") String logoUrl,
      @Value("${kkrepo.ui.favicon-url:}") String faviconUrl) {
    this.uiSettingsDao = uiSettingsDao;
    this.productName = productName;
    this.productSubtitle = productSubtitle;
    this.logoText = logoText;
    this.logoUrl = logoUrl;
    this.faviconUrl = faviconUrl;
  }

  @GetMapping
  public UiSettingsView read() {
    return toView(uiSettingsDao.read());
  }

  @GetMapping("/branding")
  public BrandingView branding() {
    return new BrandingView(productName, productSubtitle, logoText, logoUrl, faviconUrl);
  }

  @PutMapping
  public UiSettingsView update(@RequestBody UiSettingsCommand command) {
    try {
      return toView(uiSettingsDao.saveDefaultLanguage(command == null ? null : command.defaultLanguage()));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
    }
  }

  private static UiSettingsView toView(UiSettingsRecord record) {
    return new UiSettingsView(
        record.defaultLanguage(),
        SUPPORTED_DEFAULT_LANGUAGES,
        record.updatedAt());
  }

  public record UiSettingsCommand(String defaultLanguage) {
  }

  public record UiSettingsView(
      String defaultLanguage,
      List<String> supportedDefaultLanguages,
      Instant updatedAt) {
  }

  public record BrandingView(
      String productName,
      String productSubtitle,
      String logoText,
      String logoUrl,
      String faviconUrl) {
  }
}
