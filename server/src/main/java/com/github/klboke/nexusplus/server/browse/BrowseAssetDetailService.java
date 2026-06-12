package com.github.klboke.nexusplus.server.browse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.klboke.nexusplus.core.BlobReference;
import com.github.klboke.nexusplus.core.BlobStorage;
import com.github.klboke.nexusplus.core.RepositoryFormat;
import com.github.klboke.nexusplus.core.RepositoryType;
import com.github.klboke.nexusplus.persistence.mysql.dao.AssetDao;
import com.github.klboke.nexusplus.persistence.mysql.dao.RepositoryDao;
import com.github.klboke.nexusplus.persistence.mysql.model.AssetBlobRecord;
import com.github.klboke.nexusplus.persistence.mysql.model.AssetRecord;
import com.github.klboke.nexusplus.persistence.mysql.model.RepositoryRecord;
import com.github.klboke.nexusplus.persistence.mysql.support.HashColumns;
import com.github.klboke.nexusplus.protocol.npm.NpmMetadata;
import com.github.klboke.nexusplus.protocol.npm.NpmPackageId;
import com.github.klboke.nexusplus.server.blob.BlobReferenceCodec;
import com.github.klboke.nexusplus.server.maven.BlobStorageRegistry;
import com.github.klboke.nexusplus.server.npm.NpmFormatAttributes;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BrowseAssetDetailService {
  private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {};
  private static final int MAX_PACKAGE_JSON_BYTES = 1024 * 1024;

  private final RepositoryDao repositoryDao;
  private final AssetDao assetDao;
  private final BlobStorageRegistry blobStorageRegistry;
  private final ObjectMapper objectMapper;

  public BrowseAssetDetailService(
      RepositoryDao repositoryDao,
      AssetDao assetDao,
      BlobStorageRegistry blobStorageRegistry,
      ObjectMapper objectMapper) {
    this.repositoryDao = repositoryDao;
    this.assetDao = assetDao;
    this.blobStorageRegistry = blobStorageRegistry;
    this.objectMapper = objectMapper;
  }

  public BrowseAssetDetail detail(
      RepositoryRecord visibleRepository,
      String path,
      String sourceRepositoryName) {
    String storagePath = storagePath(visibleRepository.format(), path);
    ResolvedAsset resolved = resolveSourceAsset(visibleRepository, sourceRepositoryName, storagePath);
    RepositoryRecord source = resolved.source();
    AssetRecord asset = resolved.asset();
    AssetBlobRecord blob = asset.assetBlobId() == null
        ? null
        : assetDao.findBlobById(asset.assetBlobId()).orElse(null);
    if (blob == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset blob not found");
    }

    LinkedHashMap<String, Object> checksum = checksum(blob);
    LinkedHashMap<String, Object> content = content(asset);
    LinkedHashMap<String, Object> provenance = new LinkedHashMap<>();
    provenance.put("hashes_not_verified", false);
    Map<String, Object> npm = source.format() == RepositoryFormat.NPM ? npmAttributes(source, asset, blob) : Map.of();

    return new BrowseAssetDetail(
        visibleRepository.name(),
        source.name(),
        path,
        asset.name(),
        asset.size(),
        asset.contentType(),
        asset.lastUpdatedAt(),
        "/repository/" + visibleRepository.name() + "/" + storagePath,
        blob.createdBy(),
        blob.createdByIp(),
        checksum,
        content,
        npm,
        provenance);
  }

  private ResolvedAsset resolveSourceAsset(
      RepositoryRecord visibleRepository,
      String sourceRepositoryName,
      String storagePath) {
    if (visibleRepository.type() != RepositoryType.GROUP) {
      AssetRecord asset = assetDao.findAssetByPath(visibleRepository.id(), storagePath)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found"));
      return new ResolvedAsset(visibleRepository, asset);
    }
    List<RepositoryRecord> members = repositoryDao.listMembers(visibleRepository.id());
    if (sourceRepositoryName != null && !sourceRepositoryName.isBlank()) {
      RepositoryRecord source = members.stream()
          .filter(member -> member.name().equals(sourceRepositoryName))
          .findFirst()
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source repository not found"));
      AssetRecord asset = assetDao.findAssetByPath(source.id(), storagePath)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found"));
      return new ResolvedAsset(source, asset);
    }
    // Single round trip across all members instead of N sequential point queries; iterate the
    // member list so the original first-win ordering is preserved.
    List<Long> memberIds = members.stream().map(RepositoryRecord::id).toList();
    Map<Long, AssetRecord> assetsByRepository = assetDao.findAssetsByPathHash(
        memberIds, HashColumns.pathHash(storagePath));
    for (RepositoryRecord member : members) {
      AssetRecord asset = assetsByRepository.get(member.id());
      if (asset != null) {
        return new ResolvedAsset(member, asset);
      }
    }
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found in group members");
  }

  private record ResolvedAsset(RepositoryRecord source, AssetRecord asset) {}

  private String storagePath(RepositoryFormat format, String path) {
    String normalized = normalize(path);
    if (format == RepositoryFormat.PYPI
        && !normalized.isEmpty()
        && !normalized.equals("simple")
        && !normalized.startsWith("simple/")
        && !normalized.startsWith("packages/")) {
      return "packages/" + normalized;
    }
    return normalized;
  }

  private static String normalize(String path) {
    if (path == null) return "";
    String value = path.trim();
    while (value.startsWith("/")) value = value.substring(1);
    while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
    return value;
  }

  private LinkedHashMap<String, Object> checksum(AssetBlobRecord blob) {
    LinkedHashMap<String, Object> checksum = new LinkedHashMap<>();
    put(checksum, "sha1", blob.sha1());
    put(checksum, "sha256", blob.sha256());
    put(checksum, "md5", blob.md5());
    if (blob.attributes() != null) {
      put(checksum, "sha512", blob.attributes().get("sha512"));
    }
    return checksum;
  }

  private LinkedHashMap<String, Object> content(AssetRecord asset) {
    LinkedHashMap<String, Object> content = new LinkedHashMap<>();
    put(content, "last_modified", asset.lastUpdatedAt());
    return content;
  }

  private Map<String, Object> npmAttributes(RepositoryRecord source, AssetRecord asset, AssetBlobRecord blob) {
    if (!isNpmTarball(asset)) {
      return Map.of();
    }
    Optional<Map<String, Object>> packageJson = npmVersionDocument(source, asset)
        .or(() -> packageJsonFromTarball(blob));
    if (packageJson.isEmpty()) {
      return Map.of("asset_kind", "TARBALL");
    }
    LinkedHashMap<String, Object> npm = new LinkedHashMap<>(NpmFormatAttributes.extract(packageJson.get()));
    npm.put("asset_kind", "TARBALL");
    return npm;
  }

  private boolean isNpmTarball(AssetRecord asset) {
    return "tarball".equals(asset.kind()) || asset.path().endsWith(".tgz");
  }

  @SuppressWarnings("unchecked")
  private Optional<Map<String, Object>> npmVersionDocument(RepositoryRecord source, AssetRecord asset) {
    NpmTarballPath tarball = NpmTarballPath.parse(asset.path()).orElse(null);
    if (tarball == null) {
      return Optional.empty();
    }
    Optional<AssetRecord> packageRootAsset = assetDao.findAssetByPath(source.id(), tarball.packageId().id());
    if (packageRootAsset.isEmpty() || packageRootAsset.get().assetBlobId() == null) {
      return Optional.empty();
    }
    Optional<AssetBlobRecord> rootBlob = assetDao.findBlobById(packageRootAsset.get().assetBlobId());
    if (rootBlob.isEmpty()) {
      return Optional.empty();
    }
    try (InputStream body = openBody(rootBlob.get())) {
      Map<String, Object> root = objectMapper.readValue(body, MAP_TYPE);
      String version = NpmMetadata.findVersionForTarball(root, tarball.tarballName());
      Object explicitVersion = asset.attributes() == null ? null : asset.attributes().get("version");
      if (version == null && explicitVersion != null) {
        version = explicitVersion.toString();
      }
      Object raw = version == null ? null : NpmMetadata.versions(root).get(version);
      if (raw instanceof Map<?, ?> versionDocument) {
        return Optional.of((Map<String, Object>) versionDocument);
      }
    } catch (IOException | RuntimeException ignored) {
      return Optional.empty();
    }
    return Optional.empty();
  }

  private Optional<Map<String, Object>> packageJsonFromTarball(AssetBlobRecord blob) {
    try (InputStream raw = openBody(blob);
         GzipCompressorInputStream gzip = new GzipCompressorInputStream(raw);
         TarArchiveInputStream tar = new TarArchiveInputStream(gzip)) {
      TarArchiveEntry entry;
      while ((entry = tar.getNextTarEntry()) != null) {
        if (!entry.isFile() || !"package/package.json".equals(entry.getName())) {
          continue;
        }
        byte[] bytes = readLimited(tar, MAX_PACKAGE_JSON_BYTES);
        return Optional.of(objectMapper.readValue(new ByteArrayInputStream(bytes), MAP_TYPE));
      }
    } catch (IOException | RuntimeException ignored) {
      return Optional.empty();
    }
    return Optional.empty();
  }

  private InputStream openBody(AssetBlobRecord blob) {
    BlobStorage storage = blobStorageRegistry.forBlobStoreId(blob.blobStoreId());
    return storage.get(BlobReferenceCodec.reference(blob.blobRef(), blob.objectKey(), blob.sha256(), blob.size()))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blob content not found"));
  }

  private static byte[] readLimited(InputStream in, int maxBytes) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int total = 0;
    int read;
    while ((read = in.read(buffer)) >= 0) {
      total += read;
      if (total > maxBytes) {
        throw new IOException("package.json is too large");
      }
      out.write(buffer, 0, read);
    }
    return out.toByteArray();
  }

  private static void put(Map<String, Object> map, String key, Object value) {
    if (value != null) {
      map.put(key, value);
    }
  }

  private record NpmTarballPath(NpmPackageId packageId, String tarballName) {
    static Optional<NpmTarballPath> parse(String path) {
      int marker = path == null ? -1 : path.indexOf("/-/");
      if (marker < 0) {
        return Optional.empty();
      }
      String packageId = path.substring(0, marker);
      String tarballName = path.substring(marker + 3);
      if (packageId.isBlank() || tarballName.isBlank()) {
        return Optional.empty();
      }
      try {
        return Optional.of(new NpmTarballPath(NpmPackageId.parse(packageId), tarballName));
      } catch (IllegalArgumentException e) {
        return Optional.empty();
      }
    }
  }

  public record BrowseAssetDetail(
      String repository,
      String sourceRepository,
      String path,
      String name,
      Long size,
      String contentType,
      Instant lastUpdatedAt,
      String downloadUrl,
      String uploader,
      String uploaderIp,
      Map<String, Object> checksum,
      Map<String, Object> content,
      Map<String, Object> npm,
      Map<String, Object> provenance) {}
}
