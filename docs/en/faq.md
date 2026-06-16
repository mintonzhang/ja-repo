# FAQ

## What is nexus-plus?

nexus-plus is a Nexus-compatible, self-hosted artifact repository for common package formats such as Maven, npm, PyPI, Go, Helm, NuGet, RubyGems, Yum, and Raw.

It keeps Nexus-like client URLs, protocol behavior, permissions, and migration goals while using MySQL for metadata and OSS/S3-compatible storage for blobs.

## Is nexus-plus a fork of Sonatype Nexus?

No. nexus-plus is an independent implementation. Nexus is used as a compatibility reference for client-visible behavior, but nexus-plus does not copy Nexus internals such as OrientDB, embedded Elasticsearch, Karaf, OSGi, or the Nexus task subsystem.

## Is it a full replacement for Nexus?

It depends on your usage.

nexus-plus is designed for teams that need Nexus-compatible client paths, common repository formats, MySQL-backed metadata, object storage, multi-replica-friendly behavior, and migration from existing Nexus deployments.

It is not a full clone of every Nexus feature or every Nexus UI/API endpoint. Check the [Compatibility Matrix](compatibility-matrix.md) before planning production migration.

## Which repository formats are supported?

Current supported formats:

- Maven
- npm
- PyPI
- Go
- Helm
- NuGet
- RubyGems
- Yum
- Raw

Docker / OCI Registry is in progress. See the [Docker / OCI development plan](dev/docker-repository-implementation-plan.md).

## Does nexus-plus keep the same client URLs?

For supported non-Docker formats, the main client URL shape is compatible with Nexus:

```text
/repository/<repo>/<artifact-path>
```

This helps preserve Maven, npm, pip, Helm, NuGet, RubyGems, Yum, Raw, and CI client configuration during migration.

Docker / OCI uses a different `/v2/...` registry protocol and is being designed separately.

## Why MySQL?

MySQL is used as the source of truth for:

- Repository metadata.
- Components and assets.
- Users, roles, permissions, sessions, API keys, and audit logs.
- Migration state.
- Cross-replica coordination state.

This avoids relying on embedded databases or local-only state for production correctness.

## Does nexus-plus require Redis?

No. The default cache backend is process-local memory, and correctness is backed by MySQL. In-process caches are rebuildable hot caches with TTL or MySQL-backed invalidation watermarks.

## Where are artifact files stored?

Artifact bytes are stored in blob storage:

- OSS/S3-compatible storage for production.
- File storage for local trials, tests, and carefully managed shared-filesystem deployments.

MySQL stores metadata and references, not large artifact bytes.

## Can I use File blob storage in production?

OSS/S3-compatible storage is recommended for production.

File blob storage is safe for production only when every replica mounts the same strongly consistent shared filesystem and file storage is explicitly enabled for production. For ordinary production deployments, use OSS/S3.

## Does nexus-plus support high availability?

nexus-plus is designed for multi-replica deployment:

- Sessions use Spring Session JDBC.
- Short-lived authentication tickets live in MySQL.
- Migration state and background worker coordination live in MySQL.
- Node-local caches are rebuildable.
- Blob content lives in shared object storage.

You still need a reliable MySQL deployment, shared blob storage, load balancing, monitoring, and backups.

## How do I migrate from Nexus?

Use the `/admin/` console:

1. Run `Nexus Metadata` preflight.
2. Run metadata migration.
3. Run `Nexus Repository Data` metadata sync.
4. Run package sync.
5. Repeat incremental sync before cutover.
6. Move traffic to nexus-plus.

See [Nexus Migration Guide](nexus-migration-guide.md) and [Migration Playbook](migration-playbook.md).

## Do users need to change client configuration after migration?

If you move the original Nexus domain to nexus-plus and keep repository names the same, most supported non-Docker clients can continue using the same `/repository/<repo>/...` URLs.

If the domain or repository names change, clients must update configuration.

## Does migration copy proxy repositories?

Hosted repositories are scanned by default. Proxy repositories can be migrated explicitly when you want historical cache data or upstream backup data. Otherwise, proxy repositories can refill from upstream after cutover.

## What happens to user passwords and API keys during migration?

Migration tries to preserve compatible security data where the source Nexus exposes enough information. Some local users may need password reset if password hashes cannot be compensated. API keys or protocol tokens may need to be reissued depending on source data availability and security policy.

Always run preflight and review migration reports before cutover.

## Is Docker / OCI supported?

Docker / OCI Registry support is in progress. The plan is to implement Docker-specific `/v2/...` routing on a dedicated Docker traffic port, with path-based repository routing first and per-repository connector ports as a later compatibility enhancement.

Do not assume Docker pull/push works through `/repository/<repo>/...`.

## Is nexus-plus production-ready?

nexus-plus is early-stage open source software with a first public release. It already includes important production-oriented architecture choices, but each deployment should validate:

- Required repository formats.
- Client compatibility.
- Migration behavior.
- Backup and restore.
- Monitoring.
- Security model.
- Load and object-storage throughput.

Use [Production Hardening Guide](production-hardening.md) before exposing production traffic.

## How do I report a bug?

Use GitHub issues and choose the closest issue template. Include:

- nexus-plus version or commit.
- Deployment mode.
- Repository format and type.
- Client command or HTTP request.
- Expected and actual behavior.
- Sanitized logs.

For Nexus behavior differences, use the compatibility issue template and include Nexus and nexus-plus responses for the same request.

## How do I report a security issue?

Do not open a public issue for exploitable vulnerabilities.

Follow [SECURITY.md](../../SECURITY.md) and report privately through GitHub Security Advisories.

## What license does nexus-plus use?

nexus-plus is licensed under the [Apache License 2.0](../../LICENSE).

## Where can I ask questions?

Use:

- GitHub issues for bugs, compatibility differences, feature requests, and documentation problems.
- The [nexus-plus Telegram group](https://t.me/+M6prtFUGnF9kYTU1) for community discussion.
- GitHub Security Advisories for exploitable security issues.

See [SUPPORT.md](../../SUPPORT.md).
