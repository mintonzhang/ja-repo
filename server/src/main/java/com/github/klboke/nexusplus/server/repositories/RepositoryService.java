package com.github.klboke.nexusplus.server.repositories;

import com.github.klboke.nexusplus.core.RepositoryFormat;
import com.github.klboke.nexusplus.core.RepositoryRecipe;
import com.github.klboke.nexusplus.core.RepositoryRecipes;
import com.github.klboke.nexusplus.core.RepositoryType;
import com.github.klboke.nexusplus.persistence.mysql.dao.BlobStoreDao;
import com.github.klboke.nexusplus.persistence.mysql.dao.RepositoryDao;
import com.github.klboke.nexusplus.persistence.mysql.dao.SecurityDao;
import com.github.klboke.nexusplus.persistence.mysql.model.BlobStoreRecord;
import com.github.klboke.nexusplus.persistence.mysql.model.RepositoryRecord;
import com.github.klboke.nexusplus.server.maven.ProxyNegativeCache;
import com.github.klboke.nexusplus.server.maven.RepositoryRuntimeRegistry;
import com.github.klboke.nexusplus.server.npm.NpmGroupPackumentCache;
import com.github.klboke.nexusplus.server.pypi.PypiGroupSimpleIndexCache;
import com.github.klboke.nexusplus.server.cache.GroupMemberAssetCache;
import com.github.klboke.nexusplus.server.cache.NexusLikeCacheController;
import com.github.klboke.nexusplus.server.repositories.RepositoryCommands.CreateCommand;
import com.github.klboke.nexusplus.server.repositories.RepositoryCommands.GroupSettings;
import com.github.klboke.nexusplus.server.repositories.RepositoryCommands.HostedSettings;
import com.github.klboke.nexusplus.server.repositories.RepositoryCommands.ProxySettings;
import com.github.klboke.nexusplus.server.repositories.RepositoryCommands.RawSettings;
import com.github.klboke.nexusplus.server.repositories.RepositoryCommands.UpdateCommand;
import com.github.klboke.nexusplus.server.security.OutboundRequestPolicy;
import com.github.klboke.nexusplus.server.security.SecurityAuthorizationCache;
import com.github.klboke.nexusplus.server.security.SecurityCatalogCache;
import com.github.klboke.nexusplus.server.security.SecurityValidationException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepositoryService {
  private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.-]{0,199}$");
  private static final Set<String> WRITE_POLICIES = Set.of("ALLOW", "ALLOW_ONCE", "DENY");
  private static final Set<String> MAVEN_VERSION_POLICIES = Set.of("RELEASE", "SNAPSHOT", "MIXED");
  private static final Set<String> MAVEN_LAYOUT_POLICIES = Set.of("STRICT", "PERMISSIVE");
  private static final Set<String> RAW_CONTENT_DISPOSITIONS = Set.of("INLINE", "ATTACHMENT");

  private final RepositoryDao repositoryDao;
  private final BlobStoreDao blobStoreDao;
  private final SecurityDao securityDao;
  private final RepositoryRuntimeRegistry runtimeRegistry;
  private final ProxyNegativeCache proxyNegativeCache;
  private final SecurityAuthorizationCache authorizationCache;
  private final SecurityCatalogCache securityCatalogCache;
  private final OutboundRequestPolicy outboundPolicy;
  private final NpmGroupPackumentCache npmGroupPackumentCache;
  private final PypiGroupSimpleIndexCache pypiGroupSimpleIndexCache;
  private final GroupMemberAssetCache groupMemberAssetCache;
  private final NexusLikeCacheController cacheController;
  private final RepositoryCatalogCache repositoryCatalogCache;
  private final String urlPrefix;

  @Autowired
  public RepositoryService(
      RepositoryDao repositoryDao,
      BlobStoreDao blobStoreDao,
      SecurityDao securityDao,
      RepositoryRuntimeRegistry runtimeRegistry,
      ProxyNegativeCache proxyNegativeCache,
      SecurityAuthorizationCache authorizationCache,
      SecurityCatalogCache securityCatalogCache,
      OutboundRequestPolicy outboundPolicy,
      NpmGroupPackumentCache npmGroupPackumentCache,
      PypiGroupSimpleIndexCache pypiGroupSimpleIndexCache,
      GroupMemberAssetCache groupMemberAssetCache,
      NexusLikeCacheController cacheController,
      RepositoryCatalogCache repositoryCatalogCache,
      @Value("${nexus-plus.compatibility.repository-url-prefix:/repository}") String urlPrefix) {
    this.repositoryDao = repositoryDao;
    this.blobStoreDao = blobStoreDao;
    this.securityDao = securityDao;
    this.runtimeRegistry = runtimeRegistry;
    this.proxyNegativeCache = proxyNegativeCache;
    this.authorizationCache = authorizationCache;
    this.securityCatalogCache = securityCatalogCache;
    this.outboundPolicy = outboundPolicy;
    this.npmGroupPackumentCache = npmGroupPackumentCache;
    this.pypiGroupSimpleIndexCache = pypiGroupSimpleIndexCache;
    this.groupMemberAssetCache = groupMemberAssetCache;
    this.cacheController = cacheController;
    this.repositoryCatalogCache = repositoryCatalogCache;
    this.urlPrefix = urlPrefix;
  }

  public RepositoryService(
      RepositoryDao repositoryDao,
      BlobStoreDao blobStoreDao,
      SecurityDao securityDao,
      RepositoryRuntimeRegistry runtimeRegistry,
      String urlPrefix) {
    this(repositoryDao, blobStoreDao, securityDao, runtimeRegistry, null, null,
        null, OutboundRequestPolicy.allowPrivateForTests(), null, null, null, null, null, urlPrefix);
  }

  public RepositoryService(
      RepositoryDao repositoryDao,
      BlobStoreDao blobStoreDao,
      SecurityDao securityDao,
      RepositoryRuntimeRegistry runtimeRegistry,
      NexusLikeCacheController cacheController,
      String urlPrefix) {
    this(repositoryDao, blobStoreDao, securityDao, runtimeRegistry, null, null,
        null, OutboundRequestPolicy.allowPrivateForTests(), null, null, null, cacheController, null, urlPrefix);
  }

  @Transactional(readOnly = true)
  public List<RepositoryView> list() {
    if (repositoryCatalogCache != null) {
      var cached = repositoryCatalogCache.current();
      if (cached.isPresent()) {
        RepositoryCatalogCache.RepositoryCatalog catalog = cached.get();
        List<RepositoryView> result = new ArrayList<>(catalog.records().size());
        for (RepositoryRecord record : catalog.records()) {
          result.add(toView(record, catalog.blobStoreNames(), catalog.membersOf(record.id())));
        }
        return result;
      }
    }
    List<RepositoryRecord> records = repositoryDao.list();
    Map<Long, String> blobStoreNames = blobStoreNameIndex();
    List<RepositoryView> result = new ArrayList<>(records.size());
    for (RepositoryRecord record : records) {
      result.add(toView(record, blobStoreNames));
    }
    return result;
  }

  @Transactional(readOnly = true)
  public RepositoryView get(String name) {
    RepositoryRecord record = repositoryDao.findByName(name)
        .orElseThrow(() -> new RepositoryNotFoundException(name));
    return toView(record, blobStoreNameIndex());
  }

  @Transactional
  public RepositoryView create(CreateCommand command) {
    String name = requireName(command.name());
    RepositoryRecipe recipe = requireRecipe(command.recipe());
    if (repositoryDao.existsByName(name)) {
      throw new RepositoryValidationException("Repository name already exists: " + name);
    }

    boolean online = command.online() == null ? true : command.online();
    boolean strict = command.strictContentTypeValidation() == null
        ? true
        : command.strictContentTypeValidation();

    Long blobStoreId = resolveBlobStoreId(command.blobStoreName());
    Map<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("recipe", recipe.name());
    if (recipe.format() == RepositoryFormat.RAW) {
      attributes.put("raw", rawAttributes(command.raw()));
    }

    String versionPolicy = null;
    String layoutPolicy = null;
    String writePolicy = null;
    String proxyRemoteUrl = null;

    switch (recipe.type()) {
      case HOSTED -> {
        HostedSettings hosted = requireHosted(command.hosted(), recipe.format());
        writePolicy = hosted.writePolicy();
        versionPolicy = hosted.versionPolicy();
        layoutPolicy = hosted.layoutPolicy();
      }
      case PROXY -> {
        ProxySettings proxy = requireProxy(command.proxy());
        proxyRemoteUrl = proxy.remoteUrl();
        attributes.put("proxy", proxyAttributes(proxy));
      }
      case GROUP -> attributes.put("group", Map.of());
    }

    RepositoryRecord toInsert = new RepositoryRecord(
        null, name, recipe.format(), recipe.type(), recipe.name(), online,
        blobStoreId, null, proxyRemoteUrl, versionPolicy, layoutPolicy, writePolicy,
        strict, attributes);
    long id = repositoryDao.insert(toInsert);

    if (recipe.type() == RepositoryType.GROUP) {
      List<Long> memberIds = resolveMemberIds(name, recipe.format(), command.group());
      repositoryDao.replaceMembers(id, memberIds);
    }

    invalidateRuntimeCache(id, name);
    invalidateRepositoryCacheTokensAfterCommit(id);
    NexusRepositorySecurityContributor.ensureRepositoryPrivileges(securityDao, recipe.format(), name);
    invalidateAuthorizationCacheAfterCommit();
    refreshRepositoryCatalogAfterCommit();
    return get(name);
  }

  @Transactional
  public RepositoryView update(String name, UpdateCommand command) {
    RepositoryRecord existing = repositoryDao.findByName(name)
        .orElseThrow(() -> new RepositoryNotFoundException(name));
    RepositoryRecipe recipe = RepositoryRecipes.byName(existing.recipeName())
        .orElseThrow(() -> new RepositoryValidationException(
            "Stored recipe is unknown: " + existing.recipeName()));

    boolean online = command.online() == null ? existing.online() : command.online();
    boolean strict = command.strictContentTypeValidation() == null
        ? existing.strictContentTypeValidation()
        : command.strictContentTypeValidation();

    Long blobStoreId = requireUnchangedBlobStore(existing, command.blobStoreName());

    Map<String, Object> attributes = new LinkedHashMap<>(
        existing.attributes() == null ? Map.of() : existing.attributes());
    attributes.put("recipe", recipe.name());
    if (recipe.format() == RepositoryFormat.RAW) {
      RawSettings current = readRawAttributes(existing);
      RawSettings merged = command.raw() == null
          ? current
          : new RawSettings(command.raw().contentDisposition() == null
              ? current.contentDisposition()
              : command.raw().contentDisposition());
      attributes.put("raw", rawAttributes(merged));
    }

    String versionPolicy = existing.versionPolicy();
    String layoutPolicy = existing.layoutPolicy();
    String writePolicy = existing.writePolicy();
    String proxyRemoteUrl = existing.proxyRemoteUrl();

    switch (recipe.type()) {
      case HOSTED -> {
        if (command.hosted() != null) {
          HostedSettings merged = mergeHosted(existing, command.hosted());
          validateHosted(merged, recipe.format());
          writePolicy = merged.writePolicy();
          versionPolicy = merged.versionPolicy();
          layoutPolicy = merged.layoutPolicy();
        }
      }
      case PROXY -> {
        ProxySettings existingProxy = readProxyAttributes(existing);
        ProxySettings merged = mergeProxy(existingProxy, existing.proxyRemoteUrl(), command.proxy());
        validateProxy(merged);
        proxyRemoteUrl = merged.remoteUrl();
        attributes.put("proxy", proxyAttributes(merged));
      }
      case GROUP -> {
        // nothing extra on the repository row; members are replaced separately if provided
      }
    }

    RepositoryRecord toUpdate = new RepositoryRecord(
        existing.id(), existing.name(), existing.format(), existing.type(), existing.recipeName(),
        online, blobStoreId, existing.routingRuleId(), proxyRemoteUrl,
        versionPolicy, layoutPolicy, writePolicy, strict, attributes);
    repositoryDao.update(toUpdate);

    if (recipe.type() == RepositoryType.GROUP && command.group() != null) {
      List<Long> memberIds = resolveMemberIds(name, recipe.format(), command.group());
      repositoryDao.replaceMembers(existing.id(), memberIds);
      invalidateNpmGroupAfterCommit(existing.format(), existing.id());
      invalidatePypiGroupAfterCommit(existing.format(), existing.id());
      invalidateGroupMemberGroupAfterCommit(existing.format(), existing.id());
    } else if (recipe.type() != RepositoryType.GROUP) {
      invalidateNpmMemberAfterCommit(existing.format(), existing.id());
      invalidatePypiMemberAfterCommit(existing.format(), existing.id());
      invalidateGroupMemberMemberAfterCommit(existing.format(), existing.id());
    }

    invalidateRuntimeCache(existing.id(), name);
    invalidateRepositoryCacheTokensAfterCommit(existing.id());
    refreshRepositoryCatalogAfterCommit();
    return get(name);
  }

  @Transactional
  public void delete(String name) {
    RepositoryRecord existing = repositoryDao.findByName(name)
        .orElseThrow(() -> new RepositoryNotFoundException(name));
    List<RepositoryRecord> groups = repositoryDao.listGroupsContaining(existing.id());
    if (!groups.isEmpty()) {
      throw new RepositoryValidationException(
          "Repository '" + name + "' is a member of group(s): "
              + groups.stream().map(RepositoryRecord::name).toList());
    }
    if (repositoryDao.hasComponents(existing.id())) {
      throw new RepositoryValidationException(
          "Repository '" + name + "' still has components. Empty it before deletion.");
    }
    if (existing.type() == RepositoryType.GROUP) {
      repositoryDao.clearMembers(existing.id());
    }
    int removed = repositoryDao.deleteById(existing.id());
    if (removed == 0) {
      throw new RepositoryNotFoundException(name);
    }
    NexusRepositorySecurityContributor.removeRepositoryPrivileges(securityDao, existing.format(), name);
    invalidateRuntimeCache(existing.id(), name);
    invalidateRepositoryCacheTokensAfterCommit(existing.id());
    invalidateAuthorizationCacheAfterCommit();
    refreshRepositoryCatalogAfterCommit();
  }

  @Transactional
  public RepositoryView replaceMembers(String name, List<String> memberNames) {
    RepositoryRecord existing = repositoryDao.findByName(name)
        .orElseThrow(() -> new RepositoryNotFoundException(name));
    if (existing.type() != RepositoryType.GROUP) {
      throw new RepositoryValidationException("Repository '" + name + "' is not a group");
    }
    List<Long> memberIds = resolveMemberIds(name, existing.format(), new GroupSettings(memberNames));
    repositoryDao.replaceMembers(existing.id(), memberIds);
    invalidateNpmGroupAfterCommit(existing.format(), existing.id());
    invalidatePypiGroupAfterCommit(existing.format(), existing.id());
    invalidateGroupMemberGroupAfterCommit(existing.format(), existing.id());
    runtimeRegistry.invalidate(name);
    invalidateRepositoryCacheTokensAfterCommit(existing.id());
    refreshRepositoryCatalogAfterCommit();
    return get(name);
  }

  public List<RepositoryRecipe> recipes() {
    return RepositoryRecipes.list();
  }

  /**
   * Drop the cached runtime for {@code name} and every group whose member set includes it. Group
   * runtimes embed their members' resolved settings, so a change to a hosted/proxy must bust the
   * cached group too — otherwise the group keeps serving the previous member snapshot until TTL.
   */
  private void invalidateRuntimeCache(long repositoryId, String name) {
    runtimeRegistry.invalidate(name);
    if (proxyNegativeCache != null) {
      proxyNegativeCache.invalidateRepository(repositoryId);
    }
    for (RepositoryRecord group : repositoryDao.listGroupsContaining(repositoryId)) {
      runtimeRegistry.invalidate(group.name());
    }
  }

  private void invalidateRepositoryCacheTokensAfterCommit(long repositoryId) {
    if (cacheController == null) {
      return;
    }
    invalidateRepositoryCacheTokensAfterCommit(repositoryId, new HashSet<>());
  }

  private void invalidateRepositoryCacheTokensAfterCommit(long repositoryId, Set<Long> visited) {
    if (!visited.add(repositoryId)) {
      return;
    }
    cacheController.invalidateAllAfterCommit(repositoryId);
    for (RepositoryRecord group : repositoryDao.listGroupsContaining(repositoryId)) {
      Long groupId = group.id();
      if (groupId != null) {
        invalidateRepositoryCacheTokensAfterCommit(groupId, visited);
      }
    }
  }

  private void invalidateAuthorizationCacheAfterCommit() {
    if (authorizationCache != null) {
      authorizationCache.invalidateAllAfterCommit();
    }
    if (securityCatalogCache != null) {
      securityCatalogCache.refreshAfterCommit();
    }
  }

  /**
   * Reload the repository catalog snapshot after commit and broadcast a refresh so sibling replicas
   * pick up membership / config changes within the broadcast poll interval instead of via TTL.
   */
  private void refreshRepositoryCatalogAfterCommit() {
    if (repositoryCatalogCache != null) {
      repositoryCatalogCache.refreshAfterCommit();
    }
  }

  private void invalidateNpmMemberAfterCommit(RepositoryFormat format, long repositoryId) {
    if (npmGroupPackumentCache != null && format == RepositoryFormat.NPM) {
      npmGroupPackumentCache.invalidateMemberAfterCommit(repositoryId);
    }
  }

  private void invalidateNpmGroupAfterCommit(RepositoryFormat format, long groupId) {
    if (npmGroupPackumentCache != null && format == RepositoryFormat.NPM) {
      npmGroupPackumentCache.invalidateGroupAfterCommit(groupId);
    }
  }

  private void invalidatePypiMemberAfterCommit(RepositoryFormat format, long repositoryId) {
    if (pypiGroupSimpleIndexCache != null && format == RepositoryFormat.PYPI) {
      pypiGroupSimpleIndexCache.invalidateMemberAfterCommit(repositoryId);
    }
  }

  private void invalidatePypiGroupAfterCommit(RepositoryFormat format, long groupId) {
    if (pypiGroupSimpleIndexCache != null && format == RepositoryFormat.PYPI) {
      pypiGroupSimpleIndexCache.invalidateGroupAfterCommit(groupId);
    }
  }

  private void invalidateGroupMemberMemberAfterCommit(RepositoryFormat format, long repositoryId) {
    if (groupMemberAssetCache != null && (format == RepositoryFormat.NPM || format == RepositoryFormat.PYPI)) {
      groupMemberAssetCache.invalidateMemberAfterCommit(repositoryId);
    }
  }

  private void invalidateGroupMemberGroupAfterCommit(RepositoryFormat format, long groupId) {
    if (groupMemberAssetCache != null && (format == RepositoryFormat.NPM || format == RepositoryFormat.PYPI)) {
      groupMemberAssetCache.invalidateGroupAfterCommit(groupId);
    }
  }

  // ---- view assembly --------------------------------------------------------

  private RepositoryView toView(RepositoryRecord record, Map<Long, String> blobStoreNames) {
    List<String> groupMemberNames = record.type() == RepositoryType.GROUP
        ? repositoryDao.listMembers(record.id()).stream().map(RepositoryRecord::name).toList()
        : List.of();
    return toView(record, blobStoreNames, groupMemberNames);
  }

  private RepositoryView toView(
      RepositoryRecord record, Map<Long, String> blobStoreNames, List<String> groupMemberNames) {
    String blobStoreName = record.blobStoreId() == null
        ? null
        : blobStoreNames.get(record.blobStoreId());
    String url = urlPrefix.endsWith("/")
        ? urlPrefix + record.name() + "/"
        : urlPrefix + "/" + record.name() + "/";

    HostedSettings hosted = null;
    ProxySettings proxy = null;
    RawSettings raw = record.format() == RepositoryFormat.RAW ? readRawAttributes(record) : null;
    GroupSettings group = null;
    switch (record.type()) {
      case HOSTED -> hosted = new HostedSettings(
          record.writePolicy(), record.versionPolicy(), record.layoutPolicy());
      case PROXY -> proxy = readProxyAttributesOrDefaults(record);
      case GROUP -> group = new GroupSettings(groupMemberNames == null ? List.of() : groupMemberNames);
    }

    return new RepositoryView(
        record.id(), record.name(), record.recipeName(),
        record.format(), record.type(), record.online(),
        blobStoreName, record.strictContentTypeValidation(), url,
        hosted, proxy, raw, group);
  }

  private Map<Long, String> blobStoreNameIndex() {
    Map<Long, String> index = new LinkedHashMap<>();
    for (BlobStoreRecord record : blobStoreDao.list()) {
      if (record.id() != null) {
        index.put(record.id(), record.name());
      }
    }
    return index;
  }

  // ---- validation -----------------------------------------------------------

  private static String requireName(String name) {
    if (name == null || !NAME_PATTERN.matcher(name).matches()) {
      throw new RepositoryValidationException(
          "Invalid repository name. Allowed: letters, digits, '.', '_', '-' (max 200 chars).");
    }
    return name;
  }

  private static RepositoryRecipe requireRecipe(String recipeName) {
    return RepositoryRecipes.byName(recipeName)
        .orElseThrow(() -> new RepositoryValidationException(
            "Unknown recipe: " + recipeName
                + ". Known: " + RepositoryRecipes.list().stream().map(RepositoryRecipe::name).toList()));
  }

  private Long resolveBlobStoreId(String blobStoreName) {
    if (blobStoreName == null || blobStoreName.isBlank()) {
      throw new RepositoryValidationException("blobStoreName is required for repositories");
    }
    return blobStoreDao.findByName(blobStoreName)
        .map(BlobStoreRecord::id)
        .orElseThrow(() -> new RepositoryValidationException(
            "Blob store not found: " + blobStoreName));
  }

  private Long requireUnchangedBlobStore(RepositoryRecord existing, String incomingBlobStoreName) {
    if (incomingBlobStoreName == null) {
      return existing.blobStoreId();
    }
    Long incomingBlobStoreId = resolveBlobStoreId(incomingBlobStoreName);
    if (!Objects.equals(incomingBlobStoreId, existing.blobStoreId())) {
      throw new RepositoryValidationException("blobStoreName cannot be changed after repository creation");
    }
    return existing.blobStoreId();
  }

  private static HostedSettings requireHosted(HostedSettings settings, RepositoryFormat format) {
    if (settings == null) {
      throw new RepositoryValidationException("hosted settings are required for hosted repositories");
    }
    validateHosted(settings, format);
    return settings;
  }

  private static void validateHosted(HostedSettings settings, RepositoryFormat format) {
    if (settings.writePolicy() == null || !WRITE_POLICIES.contains(settings.writePolicy())) {
      throw new RepositoryValidationException(
          "hosted.writePolicy must be one of " + WRITE_POLICIES);
    }
    if (format == RepositoryFormat.MAVEN2) {
      if (settings.versionPolicy() == null || !MAVEN_VERSION_POLICIES.contains(settings.versionPolicy())) {
        throw new RepositoryValidationException(
            "hosted.versionPolicy must be one of " + MAVEN_VERSION_POLICIES + " for maven hosted");
      }
      if (settings.layoutPolicy() == null || !MAVEN_LAYOUT_POLICIES.contains(settings.layoutPolicy())) {
        throw new RepositoryValidationException(
            "hosted.layoutPolicy must be one of " + MAVEN_LAYOUT_POLICIES + " for maven hosted");
      }
    }
  }

  private static HostedSettings mergeHosted(RepositoryRecord existing, HostedSettings incoming) {
    return new HostedSettings(
        incoming.writePolicy() == null ? existing.writePolicy() : incoming.writePolicy(),
        incoming.versionPolicy() == null ? existing.versionPolicy() : incoming.versionPolicy(),
        incoming.layoutPolicy() == null ? existing.layoutPolicy() : incoming.layoutPolicy());
  }

  private ProxySettings requireProxy(ProxySettings settings) {
    if (settings == null) {
      throw new RepositoryValidationException("proxy settings are required for proxy repositories");
    }
    validateProxy(settings);
    return settings;
  }

  private void validateProxy(ProxySettings settings) {
    if (settings.remoteUrl() == null || settings.remoteUrl().isBlank()) {
      throw new RepositoryValidationException("proxy.remoteUrl is required");
    }
    try {
      outboundPolicy.validateHttpUri(settings.remoteUrl(), "proxy.remoteUrl");
    } catch (SecurityValidationException e) {
      throw new RepositoryValidationException(e.getMessage());
    }
  }

  private static ProxySettings mergeProxy(ProxySettings existing, String existingRemoteUrl, ProxySettings incoming) {
    if (incoming == null) {
      return existing == null
          ? new ProxySettings(existingRemoteUrl, null, null, null)
          : existing;
    }
    ProxySettings base = existing == null
        ? new ProxySettings(existingRemoteUrl, null, null, null)
        : existing;
    return new ProxySettings(
        incoming.remoteUrl() == null ? base.remoteUrl() : incoming.remoteUrl(),
        incoming.contentMaxAgeMinutes() == null ? base.contentMaxAgeMinutes() : incoming.contentMaxAgeMinutes(),
        incoming.metadataMaxAgeMinutes() == null ? base.metadataMaxAgeMinutes() : incoming.metadataMaxAgeMinutes(),
        incoming.autoBlock() == null ? base.autoBlock() : incoming.autoBlock());
  }

  private static Map<String, Object> proxyAttributes(ProxySettings proxy) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("remoteUrl", proxy.remoteUrl());
    if (proxy.contentMaxAgeMinutes() != null) map.put("contentMaxAgeMinutes", proxy.contentMaxAgeMinutes());
    if (proxy.metadataMaxAgeMinutes() != null) map.put("metadataMaxAgeMinutes", proxy.metadataMaxAgeMinutes());
    if (proxy.autoBlock() != null) map.put("autoBlock", proxy.autoBlock());
    return map;
  }

  private static Map<String, Object> rawAttributes(RawSettings raw) {
    RawSettings effective = raw == null ? new RawSettings("ATTACHMENT") : raw;
    String disposition = normalizeRawContentDisposition(effective.contentDisposition());
    return Map.of("contentDisposition", disposition);
  }

  private static RawSettings readRawAttributes(RepositoryRecord record) {
    Map<String, Object> attrs = record.attributes();
    Object raw = attrs == null ? null : attrs.get("raw");
    if (raw instanceof Map<?, ?> rawMap) {
      return new RawSettings(normalizeRawContentDisposition(stringValue(
          rawMap.get("contentDisposition"), "ATTACHMENT")));
    }
    return new RawSettings("ATTACHMENT");
  }

  private static String normalizeRawContentDisposition(String value) {
    String normalized = value == null || value.isBlank()
        ? "ATTACHMENT"
        : value.trim().toUpperCase(java.util.Locale.ROOT);
    if (!RAW_CONTENT_DISPOSITIONS.contains(normalized)) {
      throw new RepositoryValidationException(
          "raw.contentDisposition must be one of " + RAW_CONTENT_DISPOSITIONS);
    }
    return normalized;
  }

  private static ProxySettings readProxyAttributes(RepositoryRecord record) {
    Map<String, Object> attrs = record.attributes();
    Object raw = attrs == null ? null : attrs.get("proxy");
    if (!(raw instanceof Map<?, ?> proxyMap)) {
      return new ProxySettings(record.proxyRemoteUrl(), null, null, null);
    }
    return new ProxySettings(
        stringValue(proxyMap.get("remoteUrl"), record.proxyRemoteUrl()),
        intValue(proxyMap.get("contentMaxAgeMinutes")),
        intValue(proxyMap.get("metadataMaxAgeMinutes")),
        boolValue(proxyMap.get("autoBlock")));
  }

  private static ProxySettings readProxyAttributesOrDefaults(RepositoryRecord record) {
    ProxySettings parsed = readProxyAttributes(record);
    return parsed == null
        ? new ProxySettings(record.proxyRemoteUrl(), null, null, null)
        : parsed;
  }

  private List<Long> resolveMemberIds(String groupName, RepositoryFormat format, GroupSettings group) {
    if (group == null || group.memberNames() == null) {
      return List.of();
    }
    List<String> names = group.memberNames();
    Set<String> seen = new LinkedHashSet<>();
    List<Long> ids = new ArrayList<>(names.size());
    Set<String> badFormat = new HashSet<>();
    Set<String> nested = new HashSet<>();
    Set<String> missing = new HashSet<>();
    for (String memberName : names) {
      if (memberName == null || memberName.isBlank()) continue;
      if (memberName.equals(groupName)) {
        throw new RepositoryValidationException("Group cannot include itself: " + groupName);
      }
      if (!seen.add(memberName)) continue;
      RepositoryRecord member = repositoryDao.findByName(memberName).orElse(null);
      if (member == null) {
        missing.add(memberName);
        continue;
      }
      if (member.format() != format) {
        badFormat.add(memberName);
        continue;
      }
      if (member.type() == RepositoryType.GROUP) {
        nested.add(memberName);
        continue;
      }
      ids.add(member.id());
    }
    if (!missing.isEmpty() || !badFormat.isEmpty() || !nested.isEmpty()) {
      StringBuilder sb = new StringBuilder("Invalid group members for '").append(groupName).append("':");
      if (!missing.isEmpty()) sb.append(" missing=").append(missing);
      if (!badFormat.isEmpty()) sb.append(" wrong-format=").append(badFormat);
      if (!nested.isEmpty()) sb.append(" nested-groups=").append(nested);
      throw new RepositoryValidationException(sb.toString());
    }
    return ids;
  }

  private static String stringValue(Object value, String fallback) {
    return value == null ? fallback : value.toString();
  }

  private static Integer intValue(Object value) {
    if (value == null) return null;
    if (value instanceof Number n) return n.intValue();
    try {
      return Integer.parseInt(value.toString());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Boolean boolValue(Object value) {
    if (value == null) return null;
    if (value instanceof Boolean b) return b;
    return Boolean.parseBoolean(value.toString());
  }
}
