# Client Recipes

This guide shows common client configuration examples for repositories exposed through nexus-plus. Replace `https://nexus.example.com`, repository names, usernames, and tokens with values from your own deployment.

The main client URL pattern is:

```text
https://nexus.example.com/repository/<repo>/
```

For production, use HTTPS and avoid embedding passwords in source-controlled files. Prefer user-specific tokens or CI tokens when available.

## Maven

Use a group repository for dependency resolution and a hosted repository for deployment.

`settings.xml`:

```xml
<settings>
  <mirrors>
    <mirror>
      <id>nexus-plus</id>
      <mirrorOf>*</mirrorOf>
      <url>https://nexus.example.com/repository/maven-public/</url>
    </mirror>
  </mirrors>

  <servers>
    <server>
      <id>maven-releases</id>
      <username>alice</username>
      <password>${env.NEXUS_PLUS_PASSWORD}</password>
    </server>
    <server>
      <id>maven-snapshots</id>
      <username>alice</username>
      <password>${env.NEXUS_PLUS_PASSWORD}</password>
    </server>
  </servers>
</settings>
```

`pom.xml` deployment snippet:

```xml
<distributionManagement>
  <repository>
    <id>maven-releases</id>
    <url>https://nexus.example.com/repository/maven-releases/</url>
  </repository>
  <snapshotRepository>
    <id>maven-snapshots</id>
    <url>https://nexus.example.com/repository/maven-snapshots/</url>
  </snapshotRepository>
</distributionManagement>
```

Deploy:

```bash
mvn deploy
```

Manual PUT upload:

```bash
curl -u alice:"$NEXUS_PLUS_PASSWORD" \
  --upload-file app-1.0.0.jar \
  https://nexus.example.com/repository/maven-releases/com/acme/app/1.0.0/app-1.0.0.jar
```

## npm

Project-level `.npmrc`:

```ini
registry=https://nexus.example.com/repository/npm-group/
always-auth=true
```

Login for a hosted repository:

```bash
npm login --registry=https://nexus.example.com/repository/npm-hosted/
```

Publish:

```bash
npm publish --registry=https://nexus.example.com/repository/npm-hosted/
```

For scoped packages:

```ini
@acme:registry=https://nexus.example.com/repository/npm-group/
//nexus.example.com/repository/npm-group/:_authToken=${NPM_TOKEN}
```

Use `npm whoami --registry=...` to verify credentials.

## PyPI

`pip.conf`:

```ini
[global]
index-url = https://nexus.example.com/repository/pypi-group/simple
trusted-host = nexus.example.com
```

`~/.pypirc`:

```ini
[distutils]
index-servers =
    nexus-plus

[nexus-plus]
repository = https://nexus.example.com/repository/pypi-hosted/
username = alice
password = ${NEXUS_PLUS_PASSWORD}
```

Install:

```bash
pip install --index-url https://nexus.example.com/repository/pypi-group/simple demo-package
```

Upload with twine:

```bash
python -m build
twine upload -r nexus-plus dist/*
```

## Go

Configure a Go module proxy repository or group repository:

```bash
go env -w GOPROXY=https://nexus.example.com/repository/go-group/,direct
```

For private modules:

```bash
go env -w GOPRIVATE=git.example.com/acme/*
go env -w GONOSUMDB=git.example.com/acme/*
```

Fetch:

```bash
go list -m github.com/pkg/errors@latest
go mod download github.com/pkg/errors
```

Go hosted upload is not supported. Use Go proxy/group repositories for read-through module proxy behavior.

## Helm

Add a proxy or hosted chart repository:

```bash
helm repo add acme https://nexus.example.com/repository/helm-group/
helm repo update
helm search repo acme
```

Push a chart to a hosted repository:

```bash
helm package ./charts/demo
curl -u alice:"$NEXUS_PLUS_PASSWORD" \
  --upload-file demo-1.0.0.tgz \
  https://nexus.example.com/repository/helm-hosted/demo-1.0.0.tgz
```

If using a Helm push plugin, point it at:

```text
https://nexus.example.com/repository/helm-hosted/
```

## NuGet

Add a source:

```bash
dotnet nuget add source \
  https://nexus.example.com/repository/nuget-group/v3/index.json \
  --name nexus-plus
```

Add a source with credentials:

```bash
dotnet nuget add source \
  https://nexus.example.com/repository/nuget-hosted/v3/index.json \
  --name nexus-plus-hosted \
  --username alice \
  --password "$NEXUS_PLUS_PASSWORD" \
  --store-password-in-clear-text
```

Push:

```bash
dotnet nuget push bin/Release/Demo.1.0.0.nupkg \
  --source https://nexus.example.com/repository/nuget-hosted/ \
  --api-key "$NEXUS_PLUS_API_KEY"
```

Use a `NuGetApiKey` token for `--api-key`, or use a source configured with username/password if your environment has not enabled NuGet API keys yet.

Restore:

```bash
dotnet restore --source https://nexus.example.com/repository/nuget-group/v3/index.json
```

## RubyGems

Add source:

```bash
gem sources --add https://nexus.example.com/repository/rubygems-group/ --remove https://rubygems.org/
gem sources --list
```

Push with Basic authentication:

```bash
gem push demo-1.0.0.gem \
  --host https://alice:${NEXUS_PLUS_PASSWORD}@nexus.example.com/repository/rubygems-hosted/
```

For automation, prefer a short-lived repository user or API key and avoid committing credentials into source control. nexus-plus also accepts the configured API-key header, `X-Nexus-Plus-Token`, for HTTP clients that can set custom headers.

Push endpoint for low-level clients:

```bash
curl -u "alice:${NEXUS_PLUS_PASSWORD}" \
  --data-binary @demo-1.0.0.gem \
  https://nexus.example.com/repository/rubygems-hosted/api/v1/gems
```

Install:

```bash
gem install demo --source https://nexus.example.com/repository/rubygems-group/
```

## Yum

Repository file `/etc/yum.repos.d/nexus-plus.repo`:

```ini
[nexus-plus]
name=nexus-plus
baseurl=https://nexus.example.com/repository/yum-group/
enabled=1
gpgcheck=0
```

Install:

```bash
yum clean all
yum install demo-package
```

Upload RPM to a hosted repository:

```bash
curl -u alice:"$NEXUS_PLUS_PASSWORD" \
  --upload-file demo-1.0.0-1.x86_64.rpm \
  https://nexus.example.com/repository/yum-hosted/Packages/demo-1.0.0-1.x86_64.rpm
```

## Raw

Upload:

```bash
curl -u alice:"$NEXUS_PLUS_PASSWORD" \
  --upload-file archive.tar.gz \
  https://nexus.example.com/repository/raw-hosted/releases/archive.tar.gz
```

Download:

```bash
curl -O https://nexus.example.com/repository/raw-group/releases/archive.tar.gz
```

## Docker / OCI

Docker / OCI Registry support is in progress. See the [Docker / OCI development plan](dev/docker-repository-implementation-plan.md).

The planned client shape uses a dedicated Docker port and path-based repository routing:

```text
<host>:<docker-port>/<repo>/<image>:<tag>
```

Do not assume Docker pull/push works through `/repository/<repo>/...`.

## Troubleshooting Client Configuration

- A `401` usually means missing or invalid credentials.
- A `403` usually means the user authenticated but lacks repository permission.
- A `404` on a group repository may mean no member contains the requested asset.
- Uploads require a hosted repository and add/edit permission.
- Large uploads may require reverse proxy body-size and timeout tuning.
- If a client behaves differently from Nexus, open a compatibility issue with the exact request and response from both systems.
