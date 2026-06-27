package com.github.klboke.kkrepo.server.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.klboke.kkrepo.core.RepositoryFormat;
import com.github.klboke.kkrepo.core.RepositoryType;
import com.github.klboke.kkrepo.persistence.mysql.dao.AssetDao;
import com.github.klboke.kkrepo.server.cargo.CargoHostedService;
import com.github.klboke.kkrepo.server.helm.HelmHostedService;
import com.github.klboke.kkrepo.server.maven.MavenHostedService;
import com.github.klboke.kkrepo.server.maven.RepositoryRuntime;
import com.github.klboke.kkrepo.server.maven.RepositoryRuntimeRegistry;
import com.github.klboke.kkrepo.server.npm.NpmHostedService;
import com.github.klboke.kkrepo.server.pypi.PypiHostedService;
import com.github.klboke.kkrepo.server.raw.RawHostedService;
import com.github.klboke.kkrepo.server.yum.YumService;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.multipart.MultipartFile;

class ComponentUploadServiceTest {

  @Test
  void uploadSpecsIncludeCargoSingleAssetUpload() {
    ComponentUploadService service = service(mock(CargoHostedService.class));

    assertTrue(service.definitions().stream().anyMatch(def ->
        def.format().equals("cargo")
            && !def.multipleUpload()
            && def.assetFields().size() == 1
            && def.assetFields().getFirst().name().equals("asset")));
  }

  @Test
  void cargoComponentUploadDelegatesToHostedCrateUpload() throws Exception {
    CargoHostedService cargoHosted = mock(CargoHostedService.class);
    when(cargoHosted.uploadCrate(any(RepositoryRuntime.class), any(InputStream.class), eq("alice"), eq("127.0.0.1")))
        .thenReturn("crates/demo/0.1.0/demo-0.1.0.crate");
    ComponentUploadService service = service(cargoHosted);

    ComponentUploadService.UploadResult result = service.upload(
        "cargo-hosted",
        Map.of(),
        files("cargo.asset", "demo-0.1.0.crate"),
        "alice",
        "127.0.0.1");

    assertEquals(List.of("crates/demo/0.1.0/demo-0.1.0.crate"), result.paths());
    verify(cargoHosted).uploadCrate(
        any(RepositoryRuntime.class), any(InputStream.class), eq("alice"), eq("127.0.0.1"));
  }

  @Test
  void cargoComponentUploadRejectsNonCrateAsset() throws Exception {
    CargoHostedService cargoHosted = mock(CargoHostedService.class);
    ComponentUploadService service = service(cargoHosted);

    UploadValidationException thrown = assertThrows(
        UploadValidationException.class,
        () -> service.upload("cargo-hosted", Map.of(), files("cargo.asset", "demo.txt"), "alice", "127.0.0.1"));

    assertEquals("Cargo upload requires a .crate asset", thrown.getMessage());
    verify(cargoHosted, never()).uploadCrate(any(), any(), any(), any());
  }

  private static ComponentUploadService service(CargoHostedService cargoHosted) {
    RepositoryRuntimeRegistry registry = mock(RepositoryRuntimeRegistry.class);
    when(registry.resolve("cargo-hosted")).thenReturn(Optional.of(runtime()));
    return new ComponentUploadService(
        registry,
        mock(AssetDao.class),
        mock(MavenHostedService.class),
        mock(NpmHostedService.class),
        mock(PypiHostedService.class),
        mock(HelmHostedService.class),
        cargoHosted,
        mock(RawHostedService.class),
        mock(YumService.class));
  }

  private static LinkedMultiValueMap<String, MultipartFile> files(String field, String filename) {
    LinkedMultiValueMap<String, MultipartFile> files = new LinkedMultiValueMap<>();
    files.add(field, new MockMultipartFile(
        field,
        filename,
        "application/x-tar",
        "crate".getBytes(StandardCharsets.UTF_8)));
    return files;
  }

  private static RepositoryRuntime runtime() {
    return new RepositoryRuntime(
        1L,
        "cargo-hosted",
        RepositoryFormat.CARGO,
        RepositoryType.HOSTED,
        "cargo-hosted",
        true,
        1L,
        "ALLOW_ONCE",
        null,
        null,
        true,
        null,
        null,
        null,
        List.of());
  }
}
