#!/usr/bin/env bash
set -euo pipefail

NEXUS_URL="${NEXUS_COMPAT_BASE_URL:-http://localhost:28090}"
NEXUS_REPOSITORY="${DOCKER_MIGRATION_NEXUS_REPOSITORY:-docker-hosted}"
NEXUS_USER="${NEXUS_COMPAT_USERNAME:-admin}"
NEXUS_PASSWORD="${NEXUS_COMPAT_PASSWORD:-123456}"

KKREPO_URL="${KKREPO_COMPAT_BASE_URL:-http://127.0.0.1:18090}"
KKREPO_HEALTH_URL="${KKREPO_MANAGEMENT_URL:-http://127.0.0.1:18091}/actuator/health"
KKREPO_DOCKER_REGISTRY="${DOCKER_MIGRATION_KKREPO_REGISTRY:-127.0.0.1:18183}"
KKREPO_REPOSITORY="${DOCKER_MIGRATION_KKREPO_REPOSITORY:-docker-hosted}"
KKREPO_USER="${KKREPO_COMPAT_USERNAME:-admin}"
KKREPO_PASSWORD="${KKREPO_COMPAT_PASSWORD:-123456}"
KKREPO_BLOB_PATH="${KKREPO_COMPAT_BLOB_PATH:-/tmp/kkrepo-blobs/default}"

IMAGE="${DOCKER_MIGRATION_IMAGE:-kkrepo-migration/e2e}"
TAG="${DOCKER_MIGRATION_TAG:-$(date +%Y%m%d%H%M%S)}"
PAGE_SIZE="${DOCKER_MIGRATION_PAGE_SIZE:-500}"
CONCURRENCY="${DOCKER_MIGRATION_CONCURRENCY:-2}"
WAIT_TIMEOUT_SECONDS="${DOCKER_MIGRATION_WAIT_TIMEOUT_SECONDS:-300}"

log() {
  printf '[docker-migration-e2e] %s\n' "$*" >&2
}

need() {
  if ! command -v "$1" >/dev/null 2>&1; then
    log "missing required command: $1"
    exit 2
  fi
}

json_escape() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  printf '%s' "$value"
}

wait_for_http() {
  local label="$1"
  local url="$2"
  local auth="${3:-}"
  for ((i = 1; i <= WAIT_TIMEOUT_SECONDS; i++)); do
    if [[ -n "$auth" ]]; then
      if curl -m 5 -fsS -u "$auth" "$url" >/dev/null 2>&1; then
        log "$label is ready"
        return 0
      fi
    elif curl -m 5 -fsS "$url" >/dev/null 2>&1; then
      log "$label is ready"
      return 0
    fi
    sleep 1
  done
  log "timed out waiting for $label at $url"
  exit 1
}

docker_login() {
  local registry="$1"
  local username="$2"
  local password="$3"
  log "docker login $registry"
  printf '%s\n' "$password" | docker login "$registry" --username "$username" --password-stdin >/dev/null
}

file_size() {
  wc -c <"$1" | tr -d '[:space:]'
}

file_sha256() {
  shasum -a 256 "$1" | awk '{print $1}'
}

append_query() {
  local url="$1"
  local key_value="$2"
  if [[ "$url" == *"?"* ]]; then
    printf '%s&%s' "$url" "$key_value"
  else
    printf '%s?%s' "$url" "$key_value"
  fi
}

absolute_location() {
  local location="$1"
  if [[ "$location" == http://* || "$location" == https://* ]]; then
    printf '%s' "$location"
  elif [[ "$location" == /* ]]; then
    printf '%s%s' "${NEXUS_URL%/}" "$location"
  else
    printf '%s/%s' "${NEXUS_URL%/}" "$location"
  fi
}

header_location() {
  awk 'BEGIN{IGNORECASE=1} /^Location:/ {
    sub(/\r$/, "")
    sub(/^[^:]+:[[:space:]]*/, "")
    print
  }' "$1" | tail -n 1
}

expect_status() {
  local status="$1"
  local expected="$2"
  local action="$3"
  if [[ "$status" != "$expected" ]]; then
    log "$action returned HTTP $status, expected $expected"
    exit 1
  fi
}

upload_source_blob() {
  local image="$1"
  local file="$2"
  local digest="$3"
  local upload_url="${NEXUS_URL%/}/repository/${NEXUS_REPOSITORY}/v2/${image}/blobs/uploads/"
  local complete_url status

  complete_url="$(append_query "$upload_url" "digest=$digest")"
  status="$(curl -m 60 -sS -o /dev/null -w '%{http_code}' \
    -u "$NEXUS_USER:$NEXUS_PASSWORD" \
    -X POST \
    -H "Content-Type: application/octet-stream" \
    --data-binary @"$file" \
    "$complete_url")"
  expect_status "$status" "201" "complete source blob upload"
}

put_source_manifest() {
  local image="$1"
  local tag="$2"
  local manifest_file="$3"
  local manifest_url="${NEXUS_URL%/}/repository/${NEXUS_REPOSITORY}/v2/${image}/manifests/${tag}"
  local status
  status="$(curl -m 60 -sS -o /dev/null -w '%{http_code}' \
    -u "$NEXUS_USER:$NEXUS_PASSWORD" \
    -X PUT \
    -H "Content-Type: application/vnd.docker.distribution.manifest.v2+json" \
    --data-binary @"$manifest_file" \
    "$manifest_url")"
  expect_status "$status" "201" "put source manifest"
}

push_fixture_to_source_nexus() {
  local image="$1"
  local tag="$2"
  local workdir layer_tar layer_gz config manifest
  local layer_diff_id layer_digest layer_size config_digest config_size manifest_digest

  workdir="$(mktemp -d "${TMPDIR:-/tmp}/kkrepo-docker-migration.XXXXXX")"
  layer_tar="$workdir/layer.tar"
  layer_gz="$workdir/layer.tar.gz"
  config="$workdir/config.json"
  manifest="$workdir/manifest.json"

  dd if=/dev/zero of="$layer_tar" bs=1024 count=10 >/dev/null 2>&1
  gzip -n -c "$layer_tar" >"$layer_gz"
  layer_diff_id="sha256:$(file_sha256 "$layer_tar")"
  layer_digest="sha256:$(file_sha256 "$layer_gz")"
  layer_size="$(file_size "$layer_gz")"

  printf '{"created":"2026-06-23T00:00:00Z","architecture":"amd64","os":"linux","config":{},"rootfs":{"type":"layers","diff_ids":["%s"]},"history":[{"created":"2026-06-23T00:00:00Z","created_by":"kkrepo docker migration e2e"}]}' \
    "$layer_diff_id" >"$config"
  config_digest="sha256:$(file_sha256 "$config")"
  config_size="$(file_size "$config")"

  cat >"$manifest" <<EOF
{"schemaVersion":2,"mediaType":"application/vnd.docker.distribution.manifest.v2+json","config":{"mediaType":"application/vnd.docker.container.image.v1+json","size":${config_size},"digest":"${config_digest}"},"layers":[{"mediaType":"application/vnd.docker.image.rootfs.diff.tar.gzip","size":${layer_size},"digest":"${layer_digest}"}]}
EOF
  manifest_digest="sha256:$(file_sha256 "$manifest")"

  log "uploading fixture config blob to source Nexus: $config_digest"
  upload_source_blob "$image" "$config" "$config_digest"
  log "uploading fixture layer blob to source Nexus: $layer_digest"
  upload_source_blob "$image" "$layer_gz" "$layer_digest"
  log "putting fixture manifest to source Nexus: $image:$tag $manifest_digest"
  put_source_manifest "$image" "$tag" "$manifest"

  rm -rf "$workdir"
  printf '%s' "$manifest_digest"
}

kkrepo_repo_exists() {
  local name="$1"
  curl -m 20 -fsS -u "$KKREPO_USER:$KKREPO_PASSWORD" \
    "$KKREPO_URL/internal/repositories?purpose=admin" \
    | grep -q "\"name\"[[:space:]]*:[[:space:]]*\"$name\""
}

ensure_kkrepo_blob_store() {
  if curl -m 20 -fsS -u "$KKREPO_USER:$KKREPO_PASSWORD" "$KKREPO_URL/internal/blob-stores" \
      | grep -q '"name"[[:space:]]*:[[:space:]]*"default"'; then
    log "kkrepo blob store exists: default"
    return 0
  fi
  log "creating kkrepo file blob store: default"
  curl -m 30 -fsS \
    -u "$KKREPO_USER:$KKREPO_PASSWORD" \
    -X POST \
    -H "Content-Type: application/json" \
    --data "{\"name\":\"default\",\"type\":\"file\",\"path\":\"$(json_escape "$KKREPO_BLOB_PATH")\"}" \
    "$KKREPO_URL/internal/blob-stores" >/dev/null
}

ensure_kkrepo_docker_repository() {
  if kkrepo_repo_exists "$KKREPO_REPOSITORY"; then
    log "kkrepo repository exists: $KKREPO_REPOSITORY"
    return 0
  fi
  log "creating kkrepo Docker hosted repository: $KKREPO_REPOSITORY"
  curl -m 30 -fsS \
    -u "$KKREPO_USER:$KKREPO_PASSWORD" \
    -X POST \
    -H "Content-Type: application/json" \
    --data "{
      \"name\":\"$(json_escape "$KKREPO_REPOSITORY")\",
      \"recipe\":\"docker-hosted\",
      \"online\":true,
      \"blobStoreName\":\"default\",
      \"strictContentTypeValidation\":true,
      \"hosted\":{\"writePolicy\":\"ALLOW\"},
      \"docker\":{\"connectorEnabled\":true,\"connectorPort\":18183}
    }" \
    "$KKREPO_URL/internal/repositories" >/dev/null
  curl -m 30 -fsS \
    -u "$KKREPO_USER:$KKREPO_PASSWORD" \
    -X POST \
    "$KKREPO_URL/internal/docker/connectors/refresh" >/dev/null || true
}

wait_for_migration_idle() {
  local job_id="$1"
  local path="$KKREPO_URL/internal/migration/nexus/repository-data/jobs/$job_id"
  for ((i = 1; i <= WAIT_TIMEOUT_SECONDS; i++)); do
    local body
    body="$(curl -m 20 -fsS -u "$KKREPO_USER:$KKREPO_PASSWORD" "$path")"
    log "job $job_id status: $(printf '%s' "$body" | tr '\n' ' ' | sed 's/[[:space:]]\\{1,\\}/ /g')"
    if printf '%s' "$body" | grep -q '"active"[[:space:]]*:[[:space:]]*false'; then
      if printf '%s' "$body" | grep -q '"failedAssets"[[:space:]]*:[[:space:]]*[1-9]'; then
        log "migration job has failed assets"
        exit 1
      fi
      return 0
    fi
    sleep 2
  done
  log "timed out waiting for migration job $job_id"
  exit 1
}

wait_for_discovery_ready() {
  local job_id="$1"
  local path="$KKREPO_URL/internal/migration/nexus/repository-data/jobs/$job_id"
  for ((i = 1; i <= WAIT_TIMEOUT_SECONDS; i++)); do
    local body
    body="$(curl -m 20 -fsS -u "$KKREPO_USER:$KKREPO_PASSWORD" "$path")"
    log "job $job_id discovery status: $(printf '%s' "$body" | tr '\n' ' ' | sed 's/[[:space:]]\\{1,\\}/ /g')"
    if printf '%s' "$body" | grep -q '"failedRepositories"[[:space:]]*:[[:space:]]*true'; then
      log "migration discovery failed"
      exit 1
    fi
    if ! printf '%s' "$body" | grep -q '"status"[[:space:]]*:[[:space:]]*"discovering"'; then
      if printf '%s' "$body" | grep -q '"pendingAssets"[[:space:]]*:[[:space:]]*[1-9]'; then
        return 0
      fi
      if printf '%s' "$body" | grep -q '"discoveredAssets"[[:space:]]*:[[:space:]]*[1-9]'; then
        return 0
      fi
    fi
    sleep 2
  done
  log "timed out waiting for migration discovery on job $job_id"
  exit 1
}

json_field() {
  local field="$1"
  sed -n "s/.*\"$field\"[[:space:]]*:[[:space:]]*\\([0-9][0-9]*\\).*/\\1/p" | head -n 1
}

need curl
need docker
need shasum
need gzip
need dd

wait_for_http "Nexus status endpoint" "$NEXUS_URL/service/rest/v1/status" "$NEXUS_USER:$NEXUS_PASSWORD"
wait_for_http "kkrepo health endpoint" "$KKREPO_HEALTH_URL"
wait_for_http "kkrepo repositories endpoint" "$KKREPO_URL/internal/repositories?purpose=admin" "$KKREPO_USER:$KKREPO_PASSWORD"

ensure_kkrepo_blob_store
ensure_kkrepo_docker_repository

kkrepo_ref="${KKREPO_DOCKER_REGISTRY}/${IMAGE}:${TAG}"

docker_login "$KKREPO_DOCKER_REGISTRY" "$KKREPO_USER" "$KKREPO_PASSWORD"

source_digest_value="$(push_fixture_to_source_nexus "$IMAGE" "$TAG")"

payload="{
  \"sourceBaseUrl\":\"$(json_escape "$NEXUS_URL")\",
  \"sourceUsername\":\"$(json_escape "$NEXUS_USER")\",
  \"sourcePassword\":\"$(json_escape "$NEXUS_PASSWORD")\",
  \"repositories\":[\"$(json_escape "$NEXUS_REPOSITORY")\"],
  \"pageSize\":$PAGE_SIZE,
  \"concurrency\":$CONCURRENCY,
  \"checksumValidation\":true
}"

log "starting Docker repository-data metadata migration from $NEXUS_REPOSITORY"
start_body="$(curl -m 60 -fsS \
  -u "$KKREPO_USER:$KKREPO_PASSWORD" \
  -H "Content-Type: application/json" \
  --data "$payload" \
  "$KKREPO_URL/internal/migration/nexus/repository-data/start")"
job_id="$(printf '%s' "$start_body" | json_field jobId)"
if [[ -z "$job_id" ]]; then
  log "could not parse migration job id from: $start_body"
  exit 1
fi

wait_for_discovery_ready "$job_id"

log "starting Docker package/blob migration for job $job_id"
curl -m 30 -fsS \
  -u "$KKREPO_USER:$KKREPO_PASSWORD" \
  -X POST \
  "$KKREPO_URL/internal/migration/nexus/repository-data/jobs/$job_id/packages/start" >/dev/null
wait_for_migration_idle "$job_id"

log "pulling migrated image from kkrepo: $kkrepo_ref"
docker pull "$kkrepo_ref" >/dev/null

target_digest="$(docker image inspect --format '{{index .RepoDigests 0}}' "$kkrepo_ref" 2>/dev/null || true)"
if [[ -n "$source_digest_value" && -n "$target_digest" ]]; then
  target_digest_value="${target_digest#*@}"
  if [[ "$source_digest_value" != "$target_digest_value" ]]; then
    log "digest mismatch: source=$source_digest_value target=$target_digest"
    exit 1
  fi
  log "digest verified: $target_digest_value"
fi

log "Docker migration E2E completed: job=$job_id source=${NEXUS_URL%/}/repository/${NEXUS_REPOSITORY}/v2/${IMAGE}:${TAG} target=$kkrepo_ref"
