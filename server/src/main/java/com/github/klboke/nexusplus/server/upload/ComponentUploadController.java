package com.github.klboke.nexusplus.server.upload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.klboke.nexusplus.server.security.AuthenticatedSubject;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
@RequestMapping("/service/rest")
public class ComponentUploadController {
  private final ComponentUploadService uploadService;
  private final ObjectMapper objectMapper;

  public ComponentUploadController(ComponentUploadService uploadService, ObjectMapper objectMapper) {
    this.uploadService = uploadService;
    this.objectMapper = objectMapper;
  }

  @GetMapping("/v1/formats/upload-specs")
  public List<UploadDefinition> uploadSpecs() {
    return uploadService.definitions();
  }

  @GetMapping("/v1/formats/upload-specs/{format}")
  public UploadDefinition uploadSpec(@PathVariable("format") String format) {
    return uploadService.definition(format);
  }

  @PostMapping(value = "/v1/components", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Void> uploadComponent(
      @RequestParam("repository") String repository,
      MultipartHttpServletRequest multipartRequest,
      HttpServletRequest request) throws IOException {
    uploadService.upload(repository, multipartRequest.getParameterMap(), multipartRequest.getMultiFileMap(),
        createdBy(request), request.getRemoteAddr());
    return ResponseEntity.noContent().build();
  }

  @PostMapping(value = "/internal/ui/upload/{repository}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<String> uploadFromUi(
      @PathVariable("repository") String repository,
      MultipartHttpServletRequest multipartRequest,
      HttpServletRequest request) {
    try {
      ComponentUploadService.UploadResult result = uploadService.upload(
          repository, multipartRequest.getParameterMap(), multipartRequest.getMultiFileMap(),
          createdBy(request), request.getRemoteAddr());
      return htmlTextarea(Map.of("success", true, "data", result.searchTerm()));
    } catch (Exception e) {
      return htmlTextarea(Map.of("success", false, "message", message(e)));
    }
  }

  private static String createdBy(HttpServletRequest request) {
    Object attribute = request.getAttribute(AuthenticatedSubject.REQUEST_ATTRIBUTE);
    if (attribute instanceof AuthenticatedSubject subject) {
      return subject.userId();
    }
    return "anonymous";
  }

  private ResponseEntity<String> htmlTextarea(Map<String, ?> packet) {
    String json;
    try {
      json = objectMapper.writeValueAsString(packet);
    } catch (JsonProcessingException e) {
      json = "{\"success\":false,\"message\":\"Failed to serialize upload response\"}";
    }
    String body = "<html><body><textarea>" + html(json) + "</textarea></body></html>";
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_HTML)
        .body(body);
  }

  private static String message(Exception e) {
    if (e.getMessage() != null && !e.getMessage().isBlank()) return e.getMessage();
    Throwable cause = e.getCause();
    if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
      return cause.getMessage();
    }
    return e.getClass().getSimpleName();
  }

  private static String html(String value) {
    return value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;");
  }
}
