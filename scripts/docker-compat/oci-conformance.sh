#!/usr/bin/env bash
set -euo pipefail

ROOT_URL="${OCI_ROOT_URL:-${DOCKER_OCI_ROOT_URL:-http://127.0.0.1:18180}}"
NAMESPACE="${OCI_NAMESPACE:-${DOCKER_OCI_NAMESPACE:-kkrepo-conformance}}"
USERNAME="${OCI_USERNAME:-${DOCKER_OCI_USERNAME:-admin}}"
PASSWORD="${OCI_PASSWORD:-${DOCKER_OCI_PASSWORD:-123456}}"
PULL="${OCI_TEST_PULL:-${DOCKER_OCI_TEST_PULL:-1}}"
PUSH="${OCI_TEST_PUSH:-${DOCKER_OCI_TEST_PUSH:-1}}"
CONTENT_DISCOVERY="${OCI_TEST_CONTENT_DISCOVERY:-${DOCKER_OCI_TEST_CONTENT_DISCOVERY:-1}}"
CONTENT_MANAGEMENT="${OCI_TEST_CONTENT_MANAGEMENT:-${DOCKER_OCI_TEST_CONTENT_MANAGEMENT:-1}}"
USE_DOCKER="${DOCKER_OCI_CONFORMANCE_USE_DOCKER:-auto}"
IMAGE="${OCI_CONFORMANCE_IMAGE:-ghcr.io/opencontainers/distribution-spec/conformance:v1.1.1}"
DOCKER_NETWORK="${DOCKER_OCI_CONFORMANCE_NETWORK:-host}"
REPORT_DIR="${OCI_REPORT_DIR:-${DOCKER_OCI_REPORT_DIR:-target/oci-conformance/docker}}"

log() {
  printf '[docker-oci-conformance] %s\n' "$*" >&2
}

enabled() {
  case "${1:-}" in
    1|true|TRUE|yes|YES|on|ON) return 0 ;;
    *) return 1 ;;
  esac
}

use_docker_runner() {
  case "$USE_DOCKER" in
    1|true|TRUE|yes|YES|on|ON) return 0 ;;
    0|false|FALSE|no|NO|off|OFF) return 1 ;;
    auto)
      ! command -v oci-conformance >/dev/null 2>&1
      ;;
    *)
      log "invalid DOCKER_OCI_CONFORMANCE_USE_DOCKER=$USE_DOCKER"
      exit 2
      ;;
  esac
}

export OCI_ROOT_URL="$ROOT_URL"
export OCI_NAMESPACE="$NAMESPACE"
export OCI_USERNAME="$USERNAME"
export OCI_PASSWORD="$PASSWORD"
export OCI_TEST_PULL="$PULL"
export OCI_TEST_PUSH="$PUSH"
export OCI_TEST_CONTENT_DISCOVERY="$CONTENT_DISCOVERY"
export OCI_TEST_CONTENT_MANAGEMENT="$CONTENT_MANAGEMENT"

mkdir -p "$REPORT_DIR"

log "root=${OCI_ROOT_URL} namespace=${OCI_NAMESPACE}"
log "pull=${OCI_TEST_PULL} push=${OCI_TEST_PUSH} content-discovery=${OCI_TEST_CONTENT_DISCOVERY} content-management=${OCI_TEST_CONTENT_MANAGEMENT}"

if use_docker_runner; then
  if ! command -v docker >/dev/null 2>&1; then
    log "docker is required when DOCKER_OCI_CONFORMANCE_USE_DOCKER=$USE_DOCKER"
    exit 2
  fi
  docker_args=(--rm)
  if [[ "$DOCKER_NETWORK" != "default" ]]; then
    docker_args+=(--network "$DOCKER_NETWORK")
  fi
  log "running OCI distribution conformance image ${IMAGE} on docker network ${DOCKER_NETWORK}"
  docker run "${docker_args[@]}" \
    -e OCI_ROOT_URL \
    -e OCI_NAMESPACE \
    -e OCI_USERNAME \
    -e OCI_PASSWORD \
    -e OCI_TEST_PULL \
    -e OCI_TEST_PUSH \
    -e OCI_TEST_CONTENT_DISCOVERY \
    -e OCI_TEST_CONTENT_MANAGEMENT \
    -v "$(pwd)/${REPORT_DIR}:/conformance/report" \
    "$IMAGE"
else
  if ! command -v oci-conformance >/dev/null 2>&1; then
    log "missing oci-conformance; set DOCKER_OCI_CONFORMANCE_USE_DOCKER=1 to run ${IMAGE}"
    exit 2
  fi
  log "running local oci-conformance"
  oci-conformance
fi

if enabled "$CONTENT_MANAGEMENT"; then
  log "Content Management conformance was enabled; DELETE routes and cleanup/GC semantics are part of this run"
fi

log "reports: ${REPORT_DIR}"
