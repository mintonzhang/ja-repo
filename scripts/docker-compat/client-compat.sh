#!/usr/bin/env bash
set -euo pipefail

HOSTED_REGISTRY="${DOCKER_COMPAT_HOSTED_REGISTRY:-127.0.0.1:18180}"
PROXY_REGISTRY="${DOCKER_COMPAT_PROXY_REGISTRY:-127.0.0.1:18181}"
GROUP_REGISTRY="${DOCKER_COMPAT_GROUP_REGISTRY:-127.0.0.1:18182}"
USERNAME="${DOCKER_COMPAT_USERNAME:-admin}"
PASSWORD="${DOCKER_COMPAT_PASSWORD:-123456}"
SOURCE_IMAGE="${DOCKER_COMPAT_SOURCE_IMAGE:-alpine:3.20}"
HOSTED_IMAGE="${DOCKER_COMPAT_HOSTED_IMAGE:-kkrepo-compat/client-probe}"
TAG="${DOCKER_COMPAT_TAG:-$(date +%Y%m%d%H%M%S)}"
PROXY_IMAGE="${DOCKER_COMPAT_PROXY_IMAGE:-library/alpine:3.20}"
GROUP_IMAGE="${DOCKER_COMPAT_GROUP_IMAGE:-${HOSTED_IMAGE}:${TAG}}"
RUN_ORAS="${DOCKER_COMPAT_RUN_ORAS:-auto}"
RUN_SKOPEO="${DOCKER_COMPAT_RUN_SKOPEO:-auto}"

log() {
  printf '[docker-compat] %s\n' "$*"
}

need() {
  if ! command -v "$1" >/dev/null 2>&1; then
    log "missing required command: $1"
    exit 2
  fi
}

optional_enabled() {
  local command="$1"
  local mode="$2"
  if [ "$mode" = "false" ] || [ "$mode" = "0" ]; then
    return 1
  fi
  if command -v "$command" >/dev/null 2>&1; then
    return 0
  fi
  if [ "$mode" = "true" ] || [ "$mode" = "1" ]; then
    log "missing required optional command requested by env: $command"
    exit 2
  fi
  return 1
}

docker_login() {
  local registry="$1"
  log "docker login ${registry}"
  printf '%s\n' "$PASSWORD" | docker login "$registry" --username "$USERNAME" --password-stdin >/dev/null
}

need docker

docker_login "$HOSTED_REGISTRY"
docker_login "$PROXY_REGISTRY"
docker_login "$GROUP_REGISTRY"

hosted_ref="${HOSTED_REGISTRY}/${HOSTED_IMAGE}:${TAG}"
group_ref="${GROUP_REGISTRY}/${GROUP_IMAGE}"
proxy_ref="${PROXY_REGISTRY}/${PROXY_IMAGE}"

log "pull source image ${SOURCE_IMAGE}"
docker pull "$SOURCE_IMAGE" >/dev/null

log "tag and push hosted image ${hosted_ref}"
docker tag "$SOURCE_IMAGE" "$hosted_ref"
docker push "$hosted_ref" >/dev/null

log "pull hosted image ${hosted_ref}"
docker pull "$hosted_ref" >/dev/null

log "pull proxy image ${proxy_ref}"
docker pull "$proxy_ref" >/dev/null

log "pull group image ${group_ref}"
docker pull "$group_ref" >/dev/null

if optional_enabled oras "$RUN_ORAS"; then
  artifact_ref="${HOSTED_REGISTRY}/${HOSTED_IMAGE}:oras-${TAG}"
  tmp_file="$(mktemp "${TMPDIR:-/tmp}/kkrepo-oras.XXXXXX")"
  printf 'kkrepo oras compatibility %s\n' "$TAG" > "$tmp_file"
  log "oras login ${HOSTED_REGISTRY}"
  printf '%s\n' "$PASSWORD" | oras login "$HOSTED_REGISTRY" --username "$USERNAME" --password-stdin >/dev/null
  log "oras push ${artifact_ref}"
  oras push "$artifact_ref" "$tmp_file:application/vnd.kkrepo.compat" >/dev/null
  log "oras pull ${artifact_ref}"
  oras pull "$artifact_ref" -o "$(mktemp -d "${TMPDIR:-/tmp}/kkrepo-oras-pull.XXXXXX")" >/dev/null
  rm -f "$tmp_file"
else
  log "oras not found; skipping ORAS artifact smoke"
fi

if optional_enabled skopeo "$RUN_SKOPEO"; then
  log "skopeo inspect hosted image"
  skopeo inspect --creds "${USERNAME}:${PASSWORD}" "docker://${hosted_ref}" >/dev/null
  log "skopeo inspect proxy image"
  skopeo inspect --creds "${USERNAME}:${PASSWORD}" "docker://${proxy_ref}" >/dev/null
else
  log "skopeo not found; skipping skopeo inspect smoke"
fi

log "client compatibility matrix completed"
