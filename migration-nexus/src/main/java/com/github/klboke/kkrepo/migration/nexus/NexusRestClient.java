package com.github.klboke.kkrepo.migration.nexus;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.klboke.kkrepo.migration.nexus.security.NexusSecurityExport;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class NexusRestClient {
  private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAPS =
      new TypeReference<>() {
      };
  private static final TypeReference<Map<String, Object>> MAP =
      new TypeReference<>() {
      };
  private static final TypeReference<List<String>> LIST_OF_STRINGS =
      new TypeReference<>() {
      };
  private static final String LOCAL_USER_SOURCE = "default";
  private static final String LOCAL_USERS_PATH = "/service/rest/v1/security/users?source=default";
  private static final String LOCAL_ROLES_PATH = "/service/rest/v1/security/roles?source=default";
  private static final String DOCKER_MANIFEST_ACCEPT = String.join(", ",
      "application/vnd.docker.distribution.manifest.v2+json",
      "application/vnd.docker.distribution.manifest.list.v2+json",
      "application/vnd.oci.image.manifest.v1+json",
      "application/vnd.oci.image.index.v1+json",
      "application/vnd.oci.artifact.manifest.v1+json");
  private static final String LOCAL_SECURITY_EXPORT_SCRIPT = """
      import groovy.json.JsonOutput
      import groovy.json.JsonSlurper
      import java.io.ByteArrayInputStream
      import java.util.Locale

      def request = args == null || args.trim().isEmpty()
          ? [:]
          : new JsonSlurper().parseText(args)
      def metadataEngine = String.valueOf(request.metadataEngine ?: '').trim().toUpperCase(Locale.ROOT)
      def warnings = []
      def users = []
      try {
        def security = null
        try {
          security = container.lookup('org.sonatype.nexus.security.config.SecurityConfigurationManager')
        } catch (Throwable ignored) {
          try {
            security = container.lookup('org.sonatype.nexus.security.config.SecurityConfigurationManager', 'default')
          } catch (Throwable ignoredAgain) {
            security = null
          }
        }
        if (security == null) {
          warnings << 'Source Nexus script API did not expose local password hashes: SecurityConfigurationManager unavailable'
        } else {
          users = security.listUsers().collect { user ->
            def passwordHash = null
            try {
              passwordHash = user.password
            } catch (Throwable ignored) {
              passwordHash = null
            }
            [
              userId: user.id,
              source: 'default',
              passwordHash: passwordHash
            ]
          }
        }
      } catch (Throwable e) {
        warnings << 'Source Nexus script API did not expose local password hashes: ' + e.class.name + ': ' + String.valueOf(e.message)
        users = []
      }
      def principalDetails = { bytes ->
        if (bytes == null) {
          return [:]
        }
        try {
          def uberClassLoader = container.lookup(ClassLoader.class, 'nexus-uber')
          def streamClass = this.class.classLoader
              .loadClass('org.sonatype.nexus.common.io.ObjectInputStreamWithClassLoader')
          def input = streamClass
              .getConstructor(java.io.InputStream.class, ClassLoader.class)
              .newInstance(new ByteArrayInputStream(bytes), uberClassLoader)
          try {
            def principals = input.readObject()
            return [
              primaryPrincipal: principals.primaryPrincipal == null ? null : String.valueOf(principals.primaryPrincipal),
              realmNames: principals.realmNames == null ? [] : principals.realmNames.collect { String.valueOf(it) },
              principals: principals.asList() == null ? [] : principals.asList().collect { String.valueOf(it) }
            ]
          } finally {
            input.close()
          }
        } catch (Throwable ignored) {
          return [:]
        }
      }
      def apiKeys = []
      def apiKeyDomains = { apiKeyService ->
        def domains = new LinkedHashSet()
        try {
          ['NpmToken', 'NuGetApiKey', 'RubyGemsApiKey', 'CargoToken'].each { domain ->
            try {
              if (apiKeyService.count(domain) > 0) {
                domains << domain
              }
            } catch (Throwable ignored) {
            }
          }
        } catch (Throwable ignored) {
        }
        try {
          def datastoreManager = container.lookup('org.sonatype.nexus.datastore.api.DataStoreManager')
          def datastore = null
          try {
            def selected = datastoreManager.get('nexus')
            datastore = selected == null ? null : selected.orElse(null)
          } catch (Throwable ignored) {
            datastore = null
          }
          if (datastore != null) {
            def connection = datastore.openConnection()
            try {
              def rs = connection.createStatement().executeQuery('select distinct domain from api_key_v2 order by domain')
              try {
                while (rs.next()) {
                  domains << rs.getString(1)
                }
              } finally {
                rs.close()
              }
            } finally {
              connection.close()
            }
          }
        } catch (Throwable ignored) {
        }
        return domains
      }
      def exportApiKeysFromService = { ->
        def apiKeyService = container.lookup('org.sonatype.nexus.security.authc.apikey.ApiKeyLowLevelService')
        apiKeyDomains(apiKeyService).each { domain ->
          apiKeyService.browse(domain).each { apiKey ->
            apiKeys << [
              domain: domain,
              api_key: apiKey.apiKey == null ? null : new String(apiKey.apiKey),
              primary_principal: apiKey.primaryPrincipal,
              principals: [
                primaryPrincipal: apiKey.primaryPrincipal
              ],
              created: apiKey.created == null ? null : String.valueOf(apiKey.created),
              sourceStore: 'ApiKeyLowLevelService'
            ]
          }
        }
      }
      def exportApiKeysFromOrientDb = { ->
        def securityDatabase
        def databaseClass = null
        try {
          databaseClass = this.class.classLoader.loadClass('org.sonatype.nexus.orient.DatabaseInstance')
          def namesClass = this.class.classLoader.loadClass('org.sonatype.nexus.orient.DatabaseInstanceNames')
          def securityName = namesClass.getField('SECURITY').get(null)
          securityDatabase = container.lookup(databaseClass, securityName)
        } catch (Throwable ignored) {
          try {
            securityDatabase = databaseClass == null
                ? container.lookup('org.sonatype.nexus.orient.DatabaseInstance', 'security')
                : container.lookup(databaseClass, 'security')
          } catch (Throwable ignoredAgain) {
            securityDatabase = null
          }
        }
        if (securityDatabase == null) {
          throw new IllegalStateException('OrientDB security database is unavailable on this source metadata engine')
        }
        def db = securityDatabase.connect()
        try {
          def queryClass = this.class.classLoader
              .loadClass('com.orientechnologies.orient.core.sql.query.OSQLSynchQuery')
          def query = queryClass.getConstructor(String.class).newInstance('select from api_key')
          db.query(query).each { document ->
            def principals = principalDetails(document.field('principals'))
            apiKeys << [
              domain: document.field('domain'),
              api_key: document.field('api_key'),
              primary_principal: document.field('primary_principal'),
              principals: principals,
              sourceStore: 'OrientDB'
            ]
          }
        } finally {
          db.close()
        }
      }
      def apiKeyServiceError = null
      if (metadataEngine == 'ORIENTDB') {
        try {
          exportApiKeysFromOrientDb()
        } catch (Throwable e) {
          warnings << ("Source Nexus script API did not expose API keys through the OrientDB security strategy: "
              + e.class.name + ": " + String.valueOf(e.message))
          apiKeys = []
        }
        return JsonOutput.toJson([users: users, apiKeys: apiKeys, warnings: warnings])
      }
      if (metadataEngine.startsWith('DATASTORE')) {
        try {
          exportApiKeysFromService()
        } catch (Throwable e) {
          warnings << ("Source Nexus script API did not expose API keys through the datastore security strategy: "
              + e.class.name + ": " + String.valueOf(e.message))
          apiKeys = []
        }
        return JsonOutput.toJson([users: users, apiKeys: apiKeys, warnings: warnings])
      }
      try {
        exportApiKeysFromService()
        return JsonOutput.toJson([users: users, apiKeys: apiKeys, warnings: warnings])
      } catch (Throwable ignored) {
        apiKeyServiceError = ignored
        apiKeys = []
      }
      try {
        exportApiKeysFromOrientDb()
      } catch (Throwable e) {
        def serviceMessage = apiKeyServiceError == null
            ? ''
            : '; ApiKeyLowLevelService strategy failed: ' + apiKeyServiceError.class.name + ': ' + String.valueOf(apiKeyServiceError.message)
        warnings << ("Source Nexus script API did not expose API keys through the supported security strategies: "
            + e.class.name + ": " + String.valueOf(e.message) + serviceMessage)
        apiKeys = []
      }
      return JsonOutput.toJson([users: users, apiKeys: apiKeys, warnings: warnings])
      """;
  private static final String LOCAL_REPOSITORY_DATA_EXPORT_SCRIPT = """
      import groovy.json.JsonOutput
      import groovy.json.JsonSlurper
      import java.time.Instant

      def request = args == null || args.trim().isEmpty()
          ? [:]
          : new JsonSlurper().parseText(args)
      def repositoryName = String.valueOf(request.repositoryName ?: '').trim()
      if (repositoryName.isEmpty()) {
        throw new IllegalArgumentException('repositoryName is required')
      }
      def repositoryFormat = String.valueOf(request.repositoryFormat ?: '').trim().toLowerCase()
      def metadataEngine = String.valueOf(request.metadataEngine ?: '').trim().toUpperCase()
      int pageSize = Math.max(1, Math.min(((request.pageSize ?: 1000) as Integer), 5000))
      def afterPath = request.afterPath == null ? '' : String.valueOf(request.afterPath)
      def since = request.since == null ? '' : String.valueOf(request.since).trim()
      def sinceDate = since.isEmpty() ? null : Date.from(Instant.parse(since))

      def normalize
      normalize = { value ->
        if (value == null) {
          return null
        }
        if (value instanceof byte[]) {
          try {
            return normalize(new JsonSlurper().parseText(new String(value, 'UTF-8')))
          } catch (Throwable ignored) {
            return String.valueOf(value)
          }
        }
        if (value instanceof CharSequence) {
          def raw = String.valueOf(value)
          def trimmed = raw.trim()
          if ((trimmed.startsWith('{') && trimmed.endsWith('}'))
              || (trimmed.startsWith('[') && trimmed.endsWith(']'))) {
            try {
              return normalize(new JsonSlurper().parseText(trimmed))
            } catch (Throwable ignored) {
            }
          }
          return raw
        }
        if (value instanceof Date) {
          return value.toInstant().toString()
        }
        try {
          if (value instanceof java.time.temporal.TemporalAccessor) {
            return String.valueOf(value)
          }
        } catch (Throwable ignored) {
        }
        def valueClassName = value.getClass().name
        if (valueClassName == 'com.orientechnologies.orient.core.id.ORecordId'
            || valueClassName == 'com.orientechnologies.orient.core.id.ORID') {
          return String.valueOf(value)
        }
        if (valueClassName == 'com.orientechnologies.orient.core.record.impl.ODocument') {
          return normalize(value.toMap())
        }
        if (value instanceof Map) {
          def out = new LinkedHashMap()
          value.each { k, v -> out[String.valueOf(k)] = normalize(v) }
          return out
        }
        if (value instanceof Iterable) {
          return value.collect { normalize(it) }
        }
        return value
      }
      def iso = { value -> value instanceof Date ? value.toInstant().toString() : null }
      def text = { value ->
        if (value == null) {
          return null
        }
        def s = String.valueOf(value)
        return s.isEmpty() ? null : s
      }
      def isoValue = { value ->
        if (value == null) {
          return null
        }
        if (value instanceof Date) {
          return value.toInstant().toString()
        }
        try {
          if (value instanceof java.time.temporal.TemporalAccessor) {
            return String.valueOf(value)
          }
        } catch (Throwable ignored) {
        }
        return String.valueOf(value)
      }
      def instantValue = { value ->
        if (value == null) {
          return null
        }
        if (value instanceof Date) {
          return value.toInstant()
        }
        try {
          if (value instanceof java.time.Instant) {
            return value
          }
          if (value instanceof java.time.OffsetDateTime) {
            return value.toInstant()
          }
          if (value instanceof java.time.ZonedDateTime) {
            return value.toInstant()
          }
          if (value instanceof java.time.LocalDateTime) {
            return value.atZone(java.time.ZoneOffset.UTC).toInstant()
          }
        } catch (Throwable ignored) {
        }
        try {
          return Instant.parse(String.valueOf(value))
        } catch (Throwable ignored) {
          return null
        }
      }
      def datastorePrefix = { format ->
        def prefixes = [
          maven2: 'MAVEN2',
          npm: 'NPM',
          pypi: 'PYPI',
          go: 'GO',
          helm: 'HELM',
          docker: 'DOCKER',
          nuget: 'NUGET',
          rubygems: 'RUBYGEMS',
          yum: 'YUM',
          raw: 'RAW',
          cargo: 'CARGO'
        ]
        return prefixes[format]
      }
      def sourcePathCursor = { value ->
        def path = text(value)
        if (path == null || path.startsWith('/')) {
          return path
        }
        return '/' + path
      }
      def repositoryRelativePath = { value ->
        def path = text(value)
        while (path != null && path.startsWith('/')) {
          path = path.substring(1)
        }
        return path
      }
      def emptyPage = { warnings ->
        return JsonOutput.toJson([
          repositoryName: repositoryName,
          afterPath: afterPath,
          since: since,
          nextAfterPath: afterPath,
          complete: true,
          assets: [],
          warnings: warnings
        ])
      }
      def changedSinceOrient = { asset ->
        if (sinceDate == null) {
          return true
        }
        def blobUpdated = asset.field('blob_updated')
        if (blobUpdated instanceof Date && !blobUpdated.before(sinceDate)) {
          return true
        }
        def blobCreated = asset.field('blob_created')
        if (blobCreated instanceof Date && !blobCreated.before(sinceDate)) {
          return true
        }
        if (blobUpdated == null && blobCreated == null) {
          def lastUpdated = asset.field('last_updated')
          return lastUpdated instanceof Date && !lastUpdated.before(sinceDate)
        }
        return false
      }
      def changedSinceDatastore = { row ->
        if (sinceDate == null) {
          return true
        }
        def sinceInstant = sinceDate.toInstant()
        def blobUpdated = instantValue(row.blobUpdated)
        if (blobUpdated != null && !blobUpdated.isBefore(sinceInstant)) {
          return true
        }
        def blobCreated = instantValue(row.blobCreated)
        if (blobCreated != null && !blobCreated.isBefore(sinceInstant)) {
          return true
        }
        if (blobUpdated == null && blobCreated == null) {
          def lastUpdated = instantValue(row.lastUpdated)
          return lastUpdated != null && !lastUpdated.isBefore(sinceInstant)
        }
        return false
      }

      def exportOrientDb = { ->
        def databaseClass = this.class.classLoader.loadClass('org.sonatype.nexus.orient.DatabaseInstance')
        def componentDatabase
        try {
          def namesClass = this.class.classLoader.loadClass('org.sonatype.nexus.orient.DatabaseInstanceNames')
          def componentName = namesClass.getField('COMPONENT').get(null)
          componentDatabase = container.lookup(databaseClass, componentName)
        } catch (Throwable ignored) {
          componentDatabase = container.lookup(databaseClass, 'component')
        }
        def queryClass = this.class.classLoader
            .loadClass('com.orientechnologies.orient.core.sql.query.OSQLSynchQuery')
        def db = componentDatabase.connect()
        try {
        def buckets = db.query(queryClass.getConstructor(String.class).newInstance('select from bucket where repository_name = ?'), repositoryName)
        if (buckets == null || buckets.isEmpty()) {
          return emptyPage(['source repository bucket not found: ' + repositoryName])
        }
        def bucket = buckets[0]
        def query
        def rows
        query = afterPath.isEmpty()
            ? 'select from asset where bucket = ? order by name limit ' + pageSize
            : 'select from asset where bucket = ? and name > ? order by name limit ' + pageSize
        rows = afterPath.isEmpty()
            ? db.query(queryClass.getConstructor(String.class).newInstance(query), bucket.getIdentity())
            : db.query(queryClass.getConstructor(String.class).newInstance(query), bucket.getIdentity(), afterPath)
        rows = rows ?: []
        def assets = []
        rows.each { asset ->
          if (!changedSinceOrient(asset)) {
            return
          }
          def component = asset.field('component')
          if (component != null && component.getClass().name.contains('orient.core.id')) {
            component = db.load(component)
          }
          def componentAttributes = component == null ? [:] : normalize(component.field('attributes') ?: [:])
          def assetAttributes = normalize(asset.field('attributes') ?: [:])
          assets << [
            repositoryName: repositoryName,
            assetId: String.valueOf(asset.getIdentity()),
            componentId: component == null ? null : String.valueOf(component.getIdentity()),
            path: text(asset.field('name')),
            format: text(asset.field('format')),
            namespace: component == null ? null : text(component.field('group')),
            name: component == null ? null : text(component.field('name')),
            version: component == null ? null : text(component.field('version')),
            assetKind: text(asset.field('asset_kind')),
            contentType: text(asset.field('content_type')),
            size: asset.field('size'),
            sourceBlobRef: text(asset.field('blob_ref')),
            lastUpdated: iso(asset.field('last_updated')),
            lastDownloaded: iso(asset.field('last_downloaded')),
            blobCreated: iso(asset.field('blob_created')),
            blobUpdated: iso(asset.field('blob_updated')),
            createdBy: text(asset.field('created_by')),
            createdByIp: text(asset.field('created_by_ip')),
            attributes: assetAttributes,
            componentAttributes: componentAttributes
          ]
        }
        def nextAfterPath = rows.isEmpty() ? afterPath : text(rows[-1].field('name'))
        return JsonOutput.toJson([
          repositoryName: repositoryName,
          afterPath: afterPath,
          since: since,
          nextAfterPath: nextAfterPath,
          complete: rows.size() < pageSize,
          assets: assets,
          warnings: []
        ])
      } finally {
        db.close()
      }
      }

      def exportDatastore = { ->
        def prefix = datastorePrefix(repositoryFormat)
        if (prefix == null) {
          return emptyPage(['datastore repository content exporter does not support format: ' + repositoryFormat])
        }
        def datastoreManager = container.lookup('org.sonatype.nexus.datastore.api.DataStoreManager')
        def selected = datastoreManager.get('nexus')
        def datastore = selected == null ? null : selected.orElse(null)
        if (datastore == null) {
          return emptyPage(['datastore nexus store not found'])
        }
        def connection = datastore.openConnection()
        try {
          def repositoryId = null
          def repositoryStatement = connection.prepareStatement('select id from repository where name = ?')
          try {
            repositoryStatement.setString(1, repositoryName)
            def repositoryRows = repositoryStatement.executeQuery()
            try {
              if (repositoryRows.next()) {
                repositoryId = repositoryRows.getObject(1)
              }
            } finally {
              repositoryRows.close()
            }
          } finally {
            repositoryStatement.close()
          }
          if (repositoryId == null) {
            return emptyPage(['source repository not found: ' + repositoryName])
          }
          def contentRepositoryId = null
          def contentRepositorySql = 'select repository_id from ' + prefix + '_CONTENT_REPOSITORY where config_repository_id = ?'
          def contentRepositoryStatement = connection.prepareStatement(contentRepositorySql)
          try {
            contentRepositoryStatement.setObject(1, repositoryId)
            def contentRepositoryRows = contentRepositoryStatement.executeQuery()
            try {
              if (contentRepositoryRows.next()) {
                contentRepositoryId = contentRepositoryRows.getObject(1)
              }
            } finally {
              contentRepositoryRows.close()
            }
          } finally {
            contentRepositoryStatement.close()
          }
          if (contentRepositoryId == null) {
            return emptyPage(['source content repository not found: ' + repositoryName + ' (' + prefix + ')'])
          }
          def datastoreAfterPath = sourcePathCursor(afterPath)
          def sql = '''
              select
                a.asset_id as asset_id,
                a.path as path,
                a.kind as asset_kind,
                a.component_id as asset_component_id,
                a.last_downloaded as last_downloaded,
                a.attributes as asset_attributes,
                a.created as asset_created,
                a.last_updated as asset_last_updated,
                b.asset_blob_id as asset_blob_id,
                b.blob_ref as blob_ref,
                b.blob_size as blob_size,
                b.content_type as content_type,
                b.checksums as checksums,
                b.blob_created as blob_created,
                b.created_by as created_by,
                b.created_by_ip as created_by_ip,
                b.added_to_repository as blob_updated,
                c.component_id as component_id,
                c.namespace as component_namespace,
                c.name as component_name,
                c.kind as component_kind,
                c.version as component_version,
                c.attributes as component_attributes,
                c.created as component_created,
                c.last_updated as component_last_updated
              from ''' + prefix + '''_ASSET a
              left join ''' + prefix + '''_ASSET_BLOB b on a.asset_blob_id = b.asset_blob_id
              left join ''' + prefix + '''_COMPONENT c on a.component_id = c.component_id
              where a.repository_id = ? ''' + (datastoreAfterPath == null ? '' : 'and a.path > ? ') + '''
              order by a.path
              limit ''' + pageSize
          def statement = connection.prepareStatement(sql)
          try {
            statement.setObject(1, contentRepositoryId)
            if (datastoreAfterPath != null) {
              statement.setString(2, datastoreAfterPath)
            }
            def rows = statement.executeQuery()
            try {
              def assets = []
              def rowCount = 0
              def lastPath = afterPath
              while (rows.next()) {
                rowCount++
                def row = [
                  blobUpdated: rows.getObject('blob_updated'),
                  blobCreated: rows.getObject('blob_created'),
                  lastUpdated: rows.getObject('asset_last_updated')
                ]
                lastPath = text(rows.getObject('path')) ?: lastPath
                def path = repositoryRelativePath(lastPath)
                if (!changedSinceDatastore(row)) {
                  continue
                }
                def checksums = normalize(rows.getObject('checksums'))
                assets << [
                  repositoryName: repositoryName,
                  assetId: String.valueOf(rows.getObject('asset_id')),
                  componentId: rows.getObject('component_id') == null ? null : String.valueOf(rows.getObject('component_id')),
                  path: path,
                  format: repositoryFormat,
                  namespace: text(rows.getObject('component_namespace')),
                  name: text(rows.getObject('component_name')),
                  version: text(rows.getObject('component_version')),
                  assetKind: text(rows.getObject('asset_kind')),
                  contentType: text(rows.getObject('content_type')),
                  size: rows.getObject('blob_size'),
                  sourceBlobRef: text(rows.getObject('blob_ref')),
                  lastUpdated: isoValue(rows.getObject('asset_last_updated')),
                  lastDownloaded: isoValue(rows.getObject('last_downloaded')),
                  blobCreated: isoValue(rows.getObject('blob_created')),
                  blobUpdated: isoValue(rows.getObject('blob_updated')),
                  createdBy: text(rows.getObject('created_by')),
                  createdByIp: text(rows.getObject('created_by_ip')),
                  attributes: [
                    sourceAssetAttributes: normalize(rows.getObject('asset_attributes')),
                    checksums: checksums
                  ],
                  componentAttributes: normalize(rows.getObject('component_attributes') ?: [:])
                ]
              }
              return JsonOutput.toJson([
                repositoryName: repositoryName,
                afterPath: afterPath,
                since: since,
                nextAfterPath: repositoryRelativePath(lastPath),
                complete: rowCount < pageSize,
                assets: assets,
                warnings: []
              ])
            } finally {
              rows.close()
            }
          } finally {
            statement.close()
          }
        } finally {
          connection.close()
        }
      }

      if (metadataEngine == 'ORIENTDB') {
        return exportOrientDb()
      }
      if (metadataEngine.startsWith('DATASTORE')) {
        return exportDatastore()
      }
      try {
        return exportDatastore()
      } catch (Throwable ignored) {
        return exportOrientDb()
      }
      """;
  private static final String SOURCE_PROFILE_PROBE_SCRIPT = """
      import groovy.json.JsonOutput

      def out = [
        metadataEngine: 'unknown',
        datastore: [:],
        repositorySchema: [:],
        warnings: []
      ]
      try {
        def applicationStatus = null
        try {
          applicationStatus = container.lookup('org.sonatype.nexus.common.app.ApplicationStatusSource')
        } catch (Throwable ignoredAgain) {
          applicationStatus = null
        }
        def status = applicationStatus == null ? null : applicationStatus.getSystemStatus()
        if (status != null) {
          out.nexusVersion = status.version == null ? null : String.valueOf(status.version)
        }
      } catch (Throwable ignored) {
        // Nexus also exposes the version in the Server header, so ApplicationStatusSource failures
        // are not actionable migration warnings.
      }
      def inspectConnection = { connection, evidence ->
        def meta = connection.getMetaData()
        def product = meta.getDatabaseProductName()
        def url = meta.getURL()
        out.datastore = [
          jdbcProduct: product,
          jdbcUrl: url,
          databaseMajorVersion: meta.getDatabaseMajorVersion(),
          databaseMinorVersion: meta.getDatabaseMinorVersion(),
          evidence: evidence
        ]
        def productLower = product == null ? '' : String.valueOf(product).toLowerCase()
        def urlLower = url == null ? '' : String.valueOf(url).toLowerCase()
        if (productLower.contains('h2') || urlLower.contains('jdbc:h2')) {
          out.metadataEngine = 'DATASTORE_H2'
        } else if (productLower.contains('postgres') || urlLower.contains('postgres')) {
          out.metadataEngine = 'DATASTORE_POSTGRESQL'
        } else {
          out.metadataEngine = 'DATASTORE_UNKNOWN'
        }
        def allTables = []
        def all = meta.getTables(null, null, null, null)
        try {
          while (all.next()) {
            def name = all.getString('TABLE_NAME')
            if (name != null) {
              allTables << name
            }
          }
        } finally {
          all.close()
        }
        def tables = []
        def rs = meta.getTables(null, null, 'repository', null)
        try {
          while (rs.next()) {
            tables << rs.getString('TABLE_NAME')
          }
        } finally {
          rs.close()
        }
        def columns = []
        def crs = meta.getColumns(null, null, 'repository', null)
        try {
          while (crs.next()) {
            columns << crs.getString('COLUMN_NAME')
          }
        } finally {
          crs.close()
        }
        def requiredContentColumns = [
          content_repository: ['REPOSITORY_ID', 'CONFIG_REPOSITORY_ID'],
          asset: ['ASSET_ID', 'REPOSITORY_ID', 'PATH', 'COMPONENT_ID', 'ASSET_BLOB_ID', 'LAST_DOWNLOADED', 'ATTRIBUTES', 'CREATED', 'LAST_UPDATED'],
          asset_blob: ['ASSET_BLOB_ID', 'BLOB_REF', 'BLOB_SIZE', 'CONTENT_TYPE', 'CHECKSUMS', 'BLOB_CREATED', 'CREATED_BY', 'CREATED_BY_IP', 'ADDED_TO_REPOSITORY'],
          component: ['COMPONENT_ID', 'REPOSITORY_ID', 'NAMESPACE', 'NAME', 'KIND', 'VERSION', 'ATTRIBUTES', 'CREATED', 'LAST_UPDATED']
        ]
        def datastoreFormats = [
          maven2: 'MAVEN2',
          npm: 'NPM',
          pypi: 'PYPI',
          go: 'GO',
          helm: 'HELM',
          docker: 'DOCKER',
          nuget: 'NUGET',
          rubygems: 'RUBYGEMS',
          yum: 'YUM',
          raw: 'RAW',
          cargo: 'CARGO'
        ]
        def upperTables = allTables.collect { String.valueOf(it).toUpperCase() } as Set
        def contentModels = [:]
        datastoreFormats.each { format, prefix ->
          def tableNames = [
            contentRepository: prefix + '_CONTENT_REPOSITORY',
            asset: prefix + '_ASSET',
            assetBlob: prefix + '_ASSET_BLOB',
            component: prefix + '_COMPONENT'
          ]
          def tablesPresent = tableNames.values().every { upperTables.contains(it) }
          def columnsByTable = [:]
          def requiredColumnsPresent = tablesPresent
          tableNames.each { logicalName, tableName ->
            def foundColumns = []
            def actualTableName = allTables.find { String.valueOf(it).equalsIgnoreCase(tableName) } ?: tableName
            def columnResult = meta.getColumns(null, null, actualTableName, null)
            try {
              while (columnResult.next()) {
                foundColumns << columnResult.getString('COLUMN_NAME')
              }
            } finally {
              columnResult.close()
            }
            columnsByTable[logicalName] = foundColumns
            def upperColumns = foundColumns.collect { String.valueOf(it).toUpperCase() } as Set
            def key = logicalName == 'contentRepository' ? 'content_repository'
                : logicalName == 'assetBlob' ? 'asset_blob'
                : logicalName
            def required = requiredContentColumns[key] ?: []
            requiredColumnsPresent = requiredColumnsPresent && required.every { upperColumns.contains(it) }
          }
          contentModels[format] = [
            prefix: prefix,
            tables: tableNames,
            tablesPresent: tablesPresent,
            requiredColumnsPresent: requiredColumnsPresent,
            columns: columnsByTable
          ]
        }
        out.repositorySchema = [
          tables: tables,
          columns: columns,
          datastoreTables: allTables.sort(),
          datastoreContentModels: contentModels
        ]
      }
      try {
        def datastoreManager = null
        try {
          datastoreManager = container.lookup('org.sonatype.nexus.datastore.api.DataStoreManager')
        } catch (Throwable ignored) {
          datastoreManager = null
        }
        if (datastoreManager != null) {
          def datastore = null
          try {
            def selected = datastoreManager.get('nexus')
            datastore = selected == null ? null : selected.orElse(null)
          } catch (Throwable ignored) {
            datastore = null
          }
          if (datastore == null) {
            def stores = datastoreManager.browse()
            def iterator = stores == null ? null : stores.iterator()
            datastore = iterator != null && iterator.hasNext() ? iterator.next() : null
          }
          if (datastore != null) {
            def connection = datastore.openConnection()
            try {
              inspectConnection(connection, 'DataStoreManager')
            } finally {
              connection.close()
            }
          }
        }
        def dataSource = null
        if (out.metadataEngine == 'unknown') {
          try {
            dataSource = container.lookup(javax.sql.DataSource.class, 'nexus')
          } catch (Throwable ignored) {
            try {
              dataSource = container.lookup(javax.sql.DataSource.class)
            } catch (Throwable ignoredAgain) {
              dataSource = null
            }
          }
        }
        if (out.metadataEngine == 'unknown' && dataSource != null) {
          def connection = dataSource.getConnection()
          try {
            inspectConnection(connection, 'DataSource')
          } finally {
            connection.close()
          }
        }
      } catch (Throwable e) {
        out.warnings << 'datastore probe failed: ' + e.class.name + ': ' + String.valueOf(e.message)
      }
      if (out.metadataEngine == 'unknown') {
        try {
          container.lookup('org.sonatype.nexus.orient.DatabaseInstance')
          out.metadataEngine = 'ORIENTDB'
        } catch (Throwable ignored) {
          try {
            container.lookup('org.sonatype.nexus.orient.DatabaseInstance', 'component')
            out.metadataEngine = 'ORIENTDB'
          } catch (Throwable ignoredAgain) {
            out.warnings << 'metadata engine could not be identified'
          }
        }
      }
      return JsonOutput.toJson(out)
      """;

  private final List<URI> baseUris;
  private final String authorization;
  private final ObjectMapper objectMapper;
  private final HttpTransport transport;
  private final HttpClient binaryClient;

  public NexusRestClient(
      String baseUrl,
      String username,
      String password,
      ObjectMapper objectMapper) {
    this(baseUrl, username, password, objectMapper, true);
  }

  NexusRestClient(
      String baseUrl,
      String username,
      String password,
      ObjectMapper objectMapper,
      boolean dockerLocalhostFallback) {
    this(baseUrl, username, password, objectMapper, dockerLocalhostFallback, defaultTransport());
  }

  NexusRestClient(
      String baseUrl,
      String username,
      String password,
      ObjectMapper objectMapper,
      boolean dockerLocalhostFallback,
      HttpTransport transport) {
    URI baseUri = normalizeBaseUri(baseUrl);
    this.baseUris = dockerLocalhostFallback ? candidateBaseUris(baseUri) : List.of(baseUri);
    this.authorization = basic(username, password);
    this.objectMapper = objectMapper;
    this.transport = transport == null ? defaultTransport() : transport;
    this.binaryClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
  }

  private static HttpTransport defaultTransport() {
    HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    return request -> {
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      return new HttpTextResponse(response.statusCode(), response.body(), response.headers().map());
    };
  }

  public NexusInventory readInventory() throws IOException, InterruptedException {
    SourceProbe probe = probeSource();
    List<Map<String, Object>> repositories = getList("/service/rest/v1/repositories");
    List<RepositoryDocument> repositoryDocuments = new ArrayList<>(repositories.size());
    for (Map<String, Object> repository : repositories) {
      String name = string(repository.get("name"));
      String format = string(repository.get("format"));
      String type = string(repository.get("type"));
      Map<String, Object> detail = Map.of();
      if (NexusRepositorySupport.supportedRecipe(format, type)) {
        detail = getMap("/service/rest/v1/repositories/"
            + endpointFormat(format)
            + "/"
            + lower(type)
            + "/"
            + encodePathSegment(name));
      }
      repositoryDocuments.add(new RepositoryDocument(repository, detail));
    }
    SecurityExportResult security = readSecurityExport(probe);
    return new NexusInventory(
        getList("/service/rest/v1/blobstores"),
        repositoryDocuments,
        security.export(),
        mergeWarnings(security.warnings(), probe == null ? List.of() : probe.warnings()),
        probe);
  }

  private SourceProbe probeSource() throws IOException, InterruptedException {
    String statusVersion = null;
    List<String> warnings = new ArrayList<>();
    try {
      HttpTextResponse statusResponse = getResponse("/service/rest/v1/status");
      Map<String, Object> status = parseOptionalMap(statusResponse.body());
      statusVersion = firstNonBlank(firstString(status, "version"), nexusServerVersion(statusResponse));
    } catch (IOException e) {
      warnings.add("Source Nexus REST status probe failed: " + e.getMessage());
    }

    String scriptName = "kkrepo-source-profile-" + UUID.randomUUID().toString().replace("-", "");
    Map<String, Object> createRequest = Map.of(
        "name", scriptName,
        "type", "groovy",
        "content", SOURCE_PROFILE_PROBE_SCRIPT);
    HttpTextResponse created;
    try {
      created = postJson("/service/rest/v1/script", createRequest);
    } catch (IOException e) {
      warnings.add("Source Nexus script API source profile probe failed before creation: " + e.getMessage());
      return new SourceProbe(
          statusVersion,
          false,
          false,
          false,
          "text/plain",
          "create_failed",
          "UNKNOWN",
          null,
          null,
          Map.of(),
          warnings);
    }
    if (!created.success()) {
      warnings.add("Source Nexus script API did not create source profile probe: " + created.describe());
      return new SourceProbe(
          statusVersion,
          false,
          false,
          false,
          "text/plain",
          "create_failed",
          "UNKNOWN",
          null,
          null,
          Map.of(),
          warnings);
    }
    try {
      HttpTextResponse run = postText("/service/rest/v1/script/"
          + encodePathSegment(scriptName)
          + "/run", "");
      if (!run.success()) {
        warnings.add("Source Nexus script API did not run source profile probe: " + run.describe());
        return new SourceProbe(
            statusVersion,
            true,
            false,
            false,
            "text/plain",
            "run_failed",
            "UNKNOWN",
            null,
            null,
            Map.of(),
            warnings);
      }
      Map<String, Object> document = objectMapper.readValue(run.body(), MAP);
      String result = string(document.get("result"));
      if (result == null) {
        warnings.add("Source Nexus script API returned an empty source profile probe result.");
        return new SourceProbe(
            statusVersion,
            true,
            true,
            false,
            "text/plain",
            "empty_result",
            "UNKNOWN",
            null,
            null,
            Map.of(),
            warnings);
      }
      Map<String, Object> profile = objectMapper.readValue(result, MAP);
      warnings.addAll(stringList(profile.get("warnings")));
      Map<String, Object> datastore = objectValue(profile.get("datastore"));
      return new SourceProbe(
          firstNonBlank(firstString(profile, "nexusVersion"), statusVersion),
          true,
          true,
          true,
          "text/plain",
          "ok",
          firstNonBlank(firstString(profile, "metadataEngine"), "UNKNOWN"),
          firstString(datastore, "jdbcProduct"),
          firstString(datastore, "jdbcUrl"),
          objectValue(profile.get("repositorySchema")),
          warnings);
    } finally {
      try {
        delete("/service/rest/v1/script/" + encodePathSegment(scriptName));
      } catch (IOException e) {
        warnings.add("Source Nexus script API did not delete source profile probe: " + e.getMessage());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        warnings.add("Source Nexus script API source profile probe cleanup was interrupted.");
      }
    }
  }

  public RepositoryDataScriptSession openRepositoryDataScript()
      throws IOException, InterruptedException {
    return openRepositoryDataScript(null);
  }

  public RepositoryDataScriptSession openRepositoryDataScript(SourceProbe probe)
      throws IOException, InterruptedException {
    String scriptName = "kkrepo-repository-data-export-" + UUID.randomUUID().toString().replace("-", "");
    Map<String, Object> createRequest = Map.of(
        "name", scriptName,
        "type", "groovy",
        "content", LOCAL_REPOSITORY_DATA_EXPORT_SCRIPT);
    HttpTextResponse created = postJson("/service/rest/v1/script", createRequest);
    if (!created.success()) {
      throw new IOException("Source Nexus script API did not create repository data export script: "
          + created.describe());
    }
    return new RepositoryDataScriptSession(scriptName, probe);
  }

  public HttpResponse<InputStream> getRepositoryAsset(String repositoryName, String path)
      throws IOException, InterruptedException {
    String requestPath = "/repository/"
        + encodePathSegment(repositoryName)
        + "/"
        + encodePath(path);
    IOException firstFailure = null;
    String accept = repositoryAssetAccept(path);
    for (int index = 0; index < baseUris.size(); index++) {
      HttpRequest request = requestBuilder(baseUris.get(index), requestPath)
          .header("Accept", accept)
          .GET()
          .build();
      try {
        return binaryClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
      } catch (HttpTimeoutException e) {
        IOException failure = new IOException(timeoutMessage(requestPath, request), e);
        if (index + 1 >= baseUris.size()) {
          if (firstFailure != null) {
            failure.addSuppressed(firstFailure);
          }
          throw failure;
        }
        if (firstFailure == null) {
          firstFailure = failure;
        }
      } catch (IOException e) {
        if (index + 1 >= baseUris.size()) {
          if (firstFailure != null) {
            e.addSuppressed(firstFailure);
          }
          throw e;
        }
        if (firstFailure == null) {
          firstFailure = e;
        }
      }
    }
    throw new IOException("Nexus repository asset " + requestPath + " did not return a response");
  }

  static String repositoryAssetAccept(String path) {
    if (path == null) {
      return "*/*";
    }
    String normalized = path.trim();
    if (normalized.contains("/manifests/") && (normalized.startsWith("v2/") || normalized.contains("/v2/"))) {
      return DOCKER_MANIFEST_ACCEPT;
    }
    return "*/*";
  }

  private SecurityExportResult readSecurityExport(SourceProbe probe) throws IOException, InterruptedException {
    List<Map<String, Object>> users = getList(LOCAL_USERS_PATH).stream()
        .filter(NexusRestClient::isLocalUser)
        .toList();
    SecurityScriptProbe securityScriptProbe = readLocalSecurityScriptExport(probe);
    List<Map<String, Object>> mergedUsers = mergePasswordHashes(users, securityScriptProbe.passwordHashes());
    List<Map<String, Object>> userRoleMappings = users.stream()
        .map(user -> {
          LinkedHashMap<String, Object> mapping = new LinkedHashMap<>();
          mapping.put("userId", firstString(user, "userId", "id"));
          mapping.put("source", firstString(user, "source"));
          mapping.put("roles", stringList(user.get("roles")));
          return Map.copyOf(mapping);
        })
        .toList();
    return new SecurityExportResult(new NexusSecurityExport(
        mergedUsers,
        getList(LOCAL_ROLES_PATH).stream()
            .filter(NexusRestClient::isLocalRole)
            .toList(),
        getList("/service/rest/v1/security/privileges"),
        userRoleMappings,
        securityScriptProbe.apiKeys(),
        getList("/service/rest/v1/security/content-selectors"),
        List.of(),
        List.of(),
        getMap("/service/rest/v1/security/anonymous")),
        securityScriptProbe.warnings());
  }

  private static boolean isLocalUser(Map<String, Object> user) {
    String source = firstString(user, "source");
    return source == null || LOCAL_USER_SOURCE.equalsIgnoreCase(source);
  }

  private static boolean isLocalRole(Map<String, Object> role) {
    String source = firstString(role, "source");
    return source == null || LOCAL_USER_SOURCE.equalsIgnoreCase(source);
  }

  private SecurityScriptProbe readLocalSecurityScriptExport(SourceProbe probe) throws IOException, InterruptedException {
    String scriptName = "kkrepo-security-export-" + UUID.randomUUID().toString().replace("-", "");
    Map<String, Object> createRequest = Map.of(
        "name", scriptName,
        "type", "groovy",
        "content", LOCAL_SECURITY_EXPORT_SCRIPT);
    HttpTextResponse created = postJson("/service/rest/v1/script", createRequest);
    if (!created.success()) {
      return SecurityScriptProbe.warning("Source Nexus script API did not expose local user password hashes or API keys: "
          + created.describe());
    }
    try {
      HttpTextResponse run = postText("/service/rest/v1/script/"
          + encodePathSegment(scriptName)
          + "/run", objectMapper.writeValueAsString(Map.of(
              "metadataEngine", firstNonBlank(probe == null ? null : probe.metadataEngine(), "UNKNOWN"))));
      if (!run.success()) {
        return SecurityScriptProbe.warning("Source Nexus script API did not return local user password hashes or API keys: "
            + run.describe());
      }
      Map<String, Object> document = objectMapper.readValue(run.body(), MAP);
      String result = string(document.get("result"));
      if (result == null) {
        return SecurityScriptProbe.warning("Source Nexus script API returned an empty local user password hash/API key result.");
      }
      return securityScriptProbeFromScriptResult(result);
    } finally {
      delete("/service/rest/v1/script/" + encodePathSegment(scriptName));
    }
  }

  private SecurityScriptProbe securityScriptProbeFromScriptResult(String result) throws IOException {
    Map<String, Object> document = objectMapper.readValue(result, MAP);
    Object users = document.get("users");
    LinkedHashMap<String, String> hashes = new LinkedHashMap<>();
    if (users instanceof Iterable<?> iterable) {
      for (Object item : iterable) {
        if (!(item instanceof Map<?, ?> rawUser)) {
          continue;
        }
        Map<String, Object> user = objectMap(rawUser);
        String userId = firstString(user, "userId", "id");
        String hash = firstString(user, "passwordHash", "password", "password_hash");
        if (userId != null && hash != null) {
          hashes.put(userKey(firstString(user, "source"), userId), hash);
        }
      }
    }
    return new SecurityScriptProbe(
        Map.copyOf(hashes),
        objectMaps(document.get("apiKeys")),
        stringList(document.get("warnings")));
  }

  private static List<Map<String, Object>> mergePasswordHashes(
      List<Map<String, Object>> users,
      Map<String, String> passwordHashes) {
    if (passwordHashes.isEmpty()) {
      return users;
    }
    ArrayList<Map<String, Object>> merged = new ArrayList<>(users.size());
    for (Map<String, Object> user : users) {
      String userId = firstString(user, "userId", "id");
      String key = userKey(firstString(user, "source"), userId);
      String hash = passwordHashes.get(key);
      if (hash == null || firstString(user, "passwordHash", "password", "password_hash") != null) {
        merged.add(user);
        continue;
      }
      LinkedHashMap<String, Object> copy = new LinkedHashMap<>(user);
      copy.put("passwordHash", hash);
      merged.add(Map.copyOf(copy));
    }
    return List.copyOf(merged);
  }

  private List<Map<String, Object>> getList(String path) throws IOException, InterruptedException {
    return objectMapper.readValue(get(path), LIST_OF_MAPS);
  }

  private Map<String, Object> getMap(String path) throws IOException, InterruptedException {
    return objectMapper.readValue(get(path), MAP);
  }

  private Map<String, Object> getOptionalMap(String path) throws IOException, InterruptedException {
    String body = getResponse(path).body();
    return parseOptionalMap(body);
  }

  private Map<String, Object> parseOptionalMap(String body) throws IOException {
    if (body == null || body.isBlank()) {
      return Map.of();
    }
    return objectMapper.readValue(body, MAP);
  }

  private List<String> getStringList(String path) throws IOException, InterruptedException {
    return objectMapper.readValue(get(path), LIST_OF_STRINGS);
  }

  private String get(String path) throws IOException, InterruptedException {
    return getResponse(path).body();
  }

  private HttpTextResponse getResponse(String path) throws IOException, InterruptedException {
    HttpTextResponse response = send(path, baseUri -> requestBuilder(baseUri, path)
        .GET()
        .build());
    if (!response.success()) {
      throw new IOException("Nexus API " + path + " returned HTTP " + response.statusCode()
          + ": " + truncate(response.body()));
    }
    return response;
  }

  private HttpTextResponse postJson(String path, Object body) throws IOException, InterruptedException {
    String payload = objectMapper.writeValueAsString(body);
    return send(path, baseUri -> requestBuilder(baseUri, path)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build());
  }

  private HttpTextResponse postText(String path, String body) throws IOException, InterruptedException {
    return send(path, baseUri -> requestBuilder(baseUri, path)
        .header("Content-Type", "text/plain")
        .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body))
        .build());
  }

  private void delete(String path) throws IOException, InterruptedException {
    send(path, baseUri -> requestBuilder(baseUri, path)
        .DELETE()
        .build());
  }

  private HttpTextResponse send(String path, RequestFactory factory) throws IOException, InterruptedException {
    IOException firstFailure = null;
    for (int index = 0; index < baseUris.size(); index++) {
      HttpRequest request = null;
      try {
        request = factory.build(baseUris.get(index));
        return transport.send(request);
      } catch (HttpTimeoutException e) {
        IOException failure = new IOException(timeoutMessage(path, request), e);
        if (index + 1 >= baseUris.size()) {
          if (firstFailure != null) {
            failure.addSuppressed(firstFailure);
          }
          throw failure;
        }
        if (firstFailure == null) {
          firstFailure = failure;
        }
      } catch (IOException e) {
        if (index + 1 >= baseUris.size()) {
          if (firstFailure != null) {
            e.addSuppressed(firstFailure);
          }
          throw e;
        }
        if (firstFailure == null) {
          firstFailure = e;
        }
      }
    }
    throw new IOException("Nexus API " + path + " did not return a response");
  }

  private static String timeoutMessage(String path, HttpRequest request) {
    String timeout = request == null
        ? "the configured timeout"
        : request.timeout()
            .map(duration -> duration.toSeconds() + "s")
            .orElse("the configured timeout");
    return "Nexus API " + path + " timed out after " + timeout;
  }

  private HttpRequest.Builder requestBuilder(URI baseUri, String path) {
    return HttpRequest.newBuilder(baseUri.resolve(path))
        .timeout(Duration.ofSeconds(30))
        .header("Accept", "application/json")
        .header("Authorization", authorization);
  }

  private static URI normalizeBaseUri(String baseUrl) {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("sourceBaseUrl is required");
    }
    String value = baseUrl.trim();
    if (!value.endsWith("/")) {
      value += "/";
    }
    return URI.create(value);
  }

  private static List<URI> candidateBaseUris(URI primary) {
    if (!isLocalhost(primary)) {
      return List.of(primary);
    }
    LinkedHashMap<String, URI> candidates = new LinkedHashMap<>();
    addCandidate(candidates, primary);
    addCandidate(candidates, dockerGatewayBaseUri(primary));
    addCandidate(candidates, dockerHostBaseUri(primary));
    return List.copyOf(candidates.values());
  }

  private static void addCandidate(Map<String, URI> candidates, URI candidate) {
    if (candidate != null) {
      candidates.putIfAbsent(candidate.toString(), candidate);
    }
  }

  private static URI dockerHostBaseUri(URI uri) {
    if (!isLocalhost(uri)) {
      return null;
    }
    return withHost(uri, "host.docker.internal");
  }

  private static URI dockerGatewayBaseUri(URI uri) {
    try {
      return dockerGatewayBaseUri(uri, Files.readString(Path.of("/proc/net/route")));
    } catch (IOException e) {
      return null;
    }
  }

  static URI dockerGatewayBaseUri(URI uri, String procNetRoute) {
    if (!isLocalhost(uri)) {
      return null;
    }
    String gateway = dockerDefaultGatewayHost(procNetRoute);
    return gateway == null ? null : withHost(uri, gateway);
  }

  static String dockerDefaultGatewayHost(String procNetRoute) {
    if (procNetRoute == null || procNetRoute.isBlank()) {
      return null;
    }
    for (String line : procNetRoute.split("\\R")) {
      String[] columns = line.trim().split("\\s+");
      if (columns.length < 3
          || !"00000000".equals(columns[1])
          || "00000000".equals(columns[2])) {
        continue;
      }
      try {
        long gateway = Long.parseLong(columns[2], 16);
        return (gateway & 0xff)
            + "."
            + ((gateway >> 8) & 0xff)
            + "."
            + ((gateway >> 16) & 0xff)
            + "."
            + ((gateway >> 24) & 0xff);
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private static boolean isLocalhost(URI uri) {
    String host = uri.getHost();
    if (host == null) {
      return false;
    }
    String normalized = host.toLowerCase(Locale.ROOT);
    return "localhost".equals(normalized)
        || "127.0.0.1".equals(normalized)
        || "0.0.0.0".equals(normalized)
        || "::1".equals(normalized);
  }

  private static URI withHost(URI uri, String host) {
    try {
      return new URI(
          uri.getScheme(),
          uri.getUserInfo(),
          host,
          uri.getPort(),
          uri.getPath(),
          uri.getQuery(),
          uri.getFragment());
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid sourceBaseUrl: " + uri, e);
    }
  }

  private static String basic(String username, String password) {
    if (username == null || username.isBlank()) {
      throw new IllegalArgumentException("source username is required");
    }
    String raw = username + ":" + (password == null ? "" : password);
    return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  private static String endpointFormat(String format) {
    if ("maven2".equalsIgnoreCase(format)) {
      return "maven";
    }
    return lower(format);
  }

  private static String encodePathSegment(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8)
        .replace("+", "%20");
  }

  private static String encodePath(String path) {
    if (path == null || path.isBlank()) {
      return "";
    }
    String[] segments = path.split("/", -1);
    StringBuilder encoded = new StringBuilder(path.length() + 16);
    for (int i = 0; i < segments.length; i++) {
      if (i > 0) {
        encoded.append('/');
      }
      encoded.append(encodePathSegment(segments[i]));
    }
    return encoded.toString();
  }

  private static String firstString(Map<String, Object> map, String... keys) {
    if (map == null) {
      return null;
    }
    for (String key : keys) {
      String value = string(map.get(key));
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      String normalized = string(value);
      if (normalized != null) {
        return normalized;
      }
    }
    return null;
  }

  private static List<String> stringList(Object value) {
    if (value instanceof Iterable<?> iterable) {
      ArrayList<String> values = new ArrayList<>();
      for (Object item : iterable) {
        String text = string(item);
        if (text != null) {
          values.add(text);
        }
      }
      return List.copyOf(values);
    }
    String text = string(value);
    return text == null ? List.of() : List.of(text);
  }

  private static Map<String, Object> objectMap(Map<?, ?> source) {
    LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
    source.forEach((key, value) -> {
      String normalizedKey = string(key);
      if (normalizedKey != null && value != null) {
        copy.put(normalizedKey, value);
      }
    });
    return Map.copyOf(copy);
  }

  private static List<Map<String, Object>> objectMaps(Object value) {
    if (!(value instanceof Iterable<?> iterable)) {
      return List.of();
    }
    ArrayList<Map<String, Object>> maps = new ArrayList<>();
    for (Object item : iterable) {
      if (item instanceof Map<?, ?> map) {
        maps.add(objectMap(map));
      }
    }
    return List.copyOf(maps);
  }

  private static String string(Object value) {
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? null : text;
  }

  private static String lower(String value) {
    return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
  }

  private static String userKey(String source, String userId) {
    return normalizeSource(source) + "/" + string(userId);
  }

  private static String normalizeSource(String source) {
    String normalized = string(source);
    if (normalized == null) {
      return "local";
    }
    String lower = normalized.toLowerCase(Locale.ROOT);
    if ("default".equals(lower)
        || "nexus".equals(lower)
        || "local".equals(lower)
        || "nexusauthenticatingrealm".equals(lower)
        || "nexusauthorizingrealm".equals(lower)) {
      return "local";
    }
    return lower;
  }

  private static String nexusServerVersion(HttpTextResponse response) {
    if (response == null || response.headers() == null) {
      return null;
    }
    String server = response.headers().entrySet().stream()
        .filter(entry -> "server".equalsIgnoreCase(entry.getKey()))
        .flatMap(entry -> entry.getValue().stream())
        .findFirst()
        .orElse(null);
    if (server == null) {
      return null;
    }
    String prefix = "Nexus/";
    int start = server.indexOf(prefix);
    if (start < 0) {
      return null;
    }
    start += prefix.length();
    int end = start;
    while (end < server.length() && !Character.isWhitespace(server.charAt(end))) {
      end++;
    }
    return start < end ? server.substring(start, end) : null;
  }

  private static String truncate(String body) {
    if (body == null) {
      return "";
    }
    return body.length() <= 300 ? body : body.substring(0, 300);
  }

  public record NexusInventory(
      List<Map<String, Object>> blobStores,
      List<RepositoryDocument> repositories,
      NexusSecurityExport securityExport,
      List<String> warnings,
      SourceProbe probe) {

    public NexusInventory {
      blobStores = blobStores == null ? List.of() : List.copyOf(blobStores);
      repositories = repositories == null ? List.of() : List.copyOf(repositories);
      securityExport = securityExport == null ? NexusSecurityExport.empty() : securityExport;
      warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public NexusInventory(
        List<Map<String, Object>> blobStores,
        List<RepositoryDocument> repositories,
        NexusSecurityExport securityExport,
        List<String> warnings) {
      this(blobStores, repositories, securityExport, warnings, null);
    }
  }

  public final class RepositoryDataScriptSession implements AutoCloseable {
    private final String scriptName;
    private final SourceProbe probe;
    private boolean closed;

    private RepositoryDataScriptSession(String scriptName, SourceProbe probe) {
      this.scriptName = scriptName;
      this.probe = probe;
    }

    public RepositoryAssetPage readPage(String repositoryName, String afterPath, int pageSize)
        throws IOException, InterruptedException {
      return readPage(repositoryName, afterPath, pageSize, null);
    }

    public RepositoryAssetPage readPage(String repositoryName, String afterPath, int pageSize, Instant since)
        throws IOException, InterruptedException {
      return readPage(
          repositoryName,
          repositoryFormat(repositoryName),
          firstNonBlank(probe == null ? null : probe.metadataEngine(), "UNKNOWN"),
          afterPath,
          pageSize,
          since);
    }

    public RepositoryAssetPage readPage(
        String repositoryName,
        String repositoryFormat,
        String metadataEngine,
        String afterPath,
        int pageSize,
        Instant since)
        throws IOException, InterruptedException {
      if (closed) {
        throw new IllegalStateException("repository data export script session is closed");
      }
      Map<String, Object> request = new LinkedHashMap<>();
      request.put("repositoryName", repositoryName);
      request.put("repositoryFormat", firstNonBlank(repositoryFormat, ""));
      request.put("metadataEngine", firstNonBlank(metadataEngine, "UNKNOWN"));
      request.put("afterPath", afterPath);
      request.put("pageSize", pageSize);
      if (since != null) {
        request.put("since", since.toString());
      }
      HttpTextResponse run = postText("/service/rest/v1/script/"
          + encodePathSegment(scriptName)
          + "/run", objectMapper.writeValueAsString(request));
      if (!run.success()) {
        throw new IOException("Source Nexus script API did not return repository data page for "
            + repositoryName + ": " + run.describe());
      }
      Map<String, Object> document = objectMapper.readValue(run.body(), MAP);
      String result = string(document.get("result"));
      if (result == null) {
        throw new IOException("Source Nexus script API returned an empty repository data result for "
            + repositoryName);
      }
      return repositoryAssetPageFromScriptResult(result);
    }

    @Override
    public void close() throws IOException {
      if (closed) {
        return;
      }
      closed = true;
      try {
        delete("/service/rest/v1/script/" + encodePathSegment(scriptName));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while deleting Nexus repository data export script", e);
      }
    }
  }

  private String repositoryFormat(String repositoryName) {
    if (repositoryName == null || repositoryName.isBlank()) {
      return "";
    }
    try {
      List<Map<String, Object>> repositories = getList("/service/rest/v1/repositories");
      for (Map<String, Object> repository : repositories) {
        if (repositoryName.equals(firstString(repository, "name"))) {
          return firstNonBlank(firstString(repository, "format"), "");
        }
      }
    } catch (Exception ignored) {
    }
    return "";
  }

  private RepositoryAssetPage repositoryAssetPageFromScriptResult(String result) throws IOException {
    Map<String, Object> document = objectMapper.readValue(result, MAP);
    List<RepositoryAssetMetadata> assets = objectMaps(document.get("assets")).stream()
        .map(NexusRestClient::repositoryAssetMetadata)
        .toList();
    return new RepositoryAssetPage(
        string(document.get("repositoryName")),
        string(document.get("afterPath")),
        string(document.get("nextAfterPath")),
        bool(document.get("complete")),
        assets,
        stringList(document.get("warnings")));
  }

  private static RepositoryAssetMetadata repositoryAssetMetadata(Map<String, Object> source) {
    return new RepositoryAssetMetadata(
        firstString(source, "repositoryName"),
        firstString(source, "assetId"),
        firstString(source, "componentId"),
        firstString(source, "path"),
        firstString(source, "format"),
        firstString(source, "namespace"),
        firstString(source, "name"),
        firstString(source, "version"),
        firstString(source, "assetKind"),
        firstString(source, "contentType"),
        longValue(source.get("size")),
        firstString(source, "sourceBlobRef"),
        firstString(source, "lastUpdated"),
        firstString(source, "lastDownloaded"),
        firstString(source, "blobCreated"),
        firstString(source, "blobUpdated"),
        firstString(source, "createdBy"),
        firstString(source, "createdByIp"),
        safeMap(objectValue(source.get("attributes"))),
        safeMap(objectValue(source.get("componentAttributes"))));
  }

  private static boolean bool(Object value) {
    if (value instanceof Boolean bool) {
      return bool;
    }
    return value != null && Boolean.parseBoolean(String.valueOf(value));
  }

  private static Long longValue(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Map<String, Object> objectValue(Object value) {
    if (value instanceof Map<?, ?> map) {
      return objectMap(map);
    }
    return Map.of();
  }

  public record RepositoryAssetPage(
      String repositoryName,
      String afterPath,
      String nextAfterPath,
      boolean complete,
      List<RepositoryAssetMetadata> assets,
      List<String> warnings) {

    public RepositoryAssetPage {
      assets = assets == null ? List.of() : List.copyOf(assets);
      warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
  }

  public record RepositoryAssetMetadata(
      String repositoryName,
      String assetId,
      String componentId,
      String path,
      String format,
      String namespace,
      String name,
      String version,
      String assetKind,
      String contentType,
      Long size,
      String sourceBlobRef,
      String lastUpdated,
      String lastDownloaded,
      String blobCreated,
      String blobUpdated,
      String createdBy,
      String createdByIp,
      Map<String, Object> attributes,
      Map<String, Object> componentAttributes) {

    public RepositoryAssetMetadata {
      attributes = safeMap(attributes);
      componentAttributes = safeMap(componentAttributes);
    }
  }

  private record SecurityExportResult(
      NexusSecurityExport export,
      List<String> warnings) {

    private SecurityExportResult {
      warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
  }

  private record SecurityScriptProbe(
      Map<String, String> passwordHashes,
      List<Map<String, Object>> apiKeys,
      List<String> warnings) {

    private SecurityScriptProbe {
      passwordHashes = passwordHashes == null ? Map.of() : Map.copyOf(passwordHashes);
      apiKeys = apiKeys == null ? List.of() : List.copyOf(apiKeys);
      warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    private static SecurityScriptProbe warning(String warning) {
      return new SecurityScriptProbe(Map.of(), List.of(), List.of(warning));
    }
  }

  record HttpTextResponse(
      int statusCode,
      String body,
      Map<String, List<String>> headers) {

    HttpTextResponse(int statusCode, String body) {
      this(statusCode, body, Map.of());
    }

    public HttpTextResponse {
      headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    private boolean success() {
      return statusCode >= 200 && statusCode < 300;
    }

    private String describe() {
      return "HTTP " + statusCode + (body == null || body.isBlank() ? "" : ": " + truncate(body));
    }
  }

  @FunctionalInterface
  interface HttpTransport {
    HttpTextResponse send(HttpRequest request) throws IOException, InterruptedException;
  }

  @FunctionalInterface
  private interface RequestFactory {
    HttpRequest build(URI baseUri);
  }

  public record RepositoryDocument(
      Map<String, Object> summary,
      Map<String, Object> detail) {

    public RepositoryDocument {
      summary = safeMap(summary);
      detail = safeMap(detail);
    }
  }

  public record SourceProbe(
      String nexusVersion,
      boolean scriptApiCreatable,
      boolean scriptApiRunnable,
      boolean scriptApiDeleted,
      String scriptRunContentType,
      String scriptApiStatus,
      String metadataEngine,
      String jdbcProduct,
      String jdbcUrl,
      Map<String, Object> repositorySchema,
      List<String> warnings) {

    public SourceProbe {
      repositorySchema = safeMap(repositorySchema);
      warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
  }

  private static List<String> mergeWarnings(List<String> first, List<String> second) {
    ArrayList<String> warnings = new ArrayList<>();
    if (first != null) {
      warnings.addAll(first);
    }
    if (second != null) {
      warnings.addAll(second);
    }
    return List.copyOf(warnings.stream().filter(value -> value != null && !value.isBlank()).distinct().toList());
  }

  private static Map<String, Object> safeMap(Map<String, Object> source) {
    if (source == null || source.isEmpty()) {
      return Map.of();
    }
    LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
    source.forEach((key, value) -> {
      if (key != null && value != null) {
        copy.put(key, value);
      }
    });
    return Map.copyOf(copy);
  }
}
