#!/usr/bin/env bash
set -Eeuo pipefail

STEP=0
TOTAL_STEPS=7
COMPOSE_READY=false

log() {
  printf '[%s] [nexus-plus quickstart] %s\n' "$(date '+%H:%M:%S')" "$*"
}

warn() {
  printf '[%s] [nexus-plus quickstart] WARN: %s\n' "$(date '+%H:%M:%S')" "$*" >&2
}

fail() {
  printf '[%s] [nexus-plus quickstart] ERROR: %s\n' "$(date '+%H:%M:%S')" "$*" >&2
  exit 1
}

step() {
  STEP=$((STEP + 1))
  printf '\n[%s] [nexus-plus quickstart] Step %d/%d: %s\n' "$(date '+%H:%M:%S')" "$STEP" "$TOTAL_STEPS" "$*"
}

on_error() {
  local code=$?
  warn "Quickstart stopped unexpectedly with exit code ${code}."
  if [[ "${COMPOSE_READY}" == "true" && -f "${COMPOSE_FILE:-}" ]]; then
    warn "Recent nexus-plus logs:"
    "${COMPOSE_CMD[@]}" -f "$COMPOSE_FILE" logs --tail 120 nexus-plus >&2 || true
  fi
  exit "$code"
}

trap on_error ERR

command_exists() {
  command -v "$1" >/dev/null 2>&1
}

generate_secret() {
  if command_exists openssl; then
    openssl rand -hex 32
  elif command_exists uuidgen; then
    printf 'quickstart-%s-%s\n' "$(date '+%s')" "$(uuidgen | tr -d '-')"
  else
    printf 'quickstart-%s-%s%s%s\n' "$(date '+%s')" "$RANDOM" "$RANDOM" "$RANDOM"
  fi
}

env_file_value() {
  local key=$1
  local file=$2
  awk -F= -v key="$key" '$1 == key {sub(/^[^=]*=/, ""); print; exit}' "$file"
}

load_env_file_defaults() {
  local file=$1
  local key value
  [[ -f "$file" ]] || return 0
  for key in \
    NEXUS_PLUS_IMAGE_TAG \
    NEXUS_PLUS_HTTP_PORT \
    NEXUS_PLUS_MANAGEMENT_PORT \
    NEXUS_PLUS_PROJECT_NAME \
    NEXUS_PLUS_CREDENTIAL_SECRET \
    NEXUS_PLUS_API_KEY_PAYLOAD_SECRET; do
    if [[ -z "${!key+x}" ]]; then
      value=$(env_file_value "$key" "$file" || true)
      if [[ -n "$value" ]]; then
        printf -v "$key" '%s' "$value"
        export "$key"
      fi
    fi
  done
}

create_env_file_if_missing() {
  local file=$1
  if [[ -f "$file" ]]; then
    log "Using existing environment file: $file"
    return 0
  fi

  : "${NEXUS_PLUS_CREDENTIAL_SECRET:=$(generate_secret)}"
  : "${NEXUS_PLUS_API_KEY_PAYLOAD_SECRET:=$(generate_secret)}"
  export NEXUS_PLUS_CREDENTIAL_SECRET NEXUS_PLUS_API_KEY_PAYLOAD_SECRET

  cat >"$file" <<EOF
NEXUS_PLUS_IMAGE_TAG=${NEXUS_PLUS_IMAGE_TAG}
NEXUS_PLUS_HTTP_PORT=${NEXUS_PLUS_HTTP_PORT}
NEXUS_PLUS_MANAGEMENT_PORT=${NEXUS_PLUS_MANAGEMENT_PORT}
NEXUS_PLUS_PROJECT_NAME=${NEXUS_PLUS_PROJECT_NAME}
NEXUS_PLUS_CREDENTIAL_SECRET=${NEXUS_PLUS_CREDENTIAL_SECRET}
NEXUS_PLUS_API_KEY_PAYLOAD_SECRET=${NEXUS_PLUS_API_KEY_PAYLOAD_SECRET}
EOF
  log "Created environment file: $file"
}

resolve_compose_command() {
  if docker compose version >/dev/null 2>&1; then
    COMPOSE_CMD=(docker compose)
    COMPOSE_DISPLAY="docker compose"
  elif command_exists docker-compose; then
    COMPOSE_CMD=(docker-compose)
    COMPOSE_DISPLAY="docker-compose"
  else
    fail "Docker Compose is required. Install Docker Desktop or the docker compose plugin."
  fi
}

port_in_use() {
  local port=$1
  if command_exists lsof; then
    lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
  elif command_exists nc; then
    nc -z 127.0.0.1 "$port" >/dev/null 2>&1
  else
    return 1
  fi
}

download_compose_file() {
  local destination=$1
  local local_compose

  if [[ -n "${SCRIPT_DIR:-}" ]]; then
    local_compose="${SCRIPT_DIR}/../docker-compose.quickstart.yml"
    if [[ -f "$local_compose" && "${NEXUS_PLUS_FORCE_DOWNLOAD:-false}" != "true" ]]; then
      cp "$local_compose" "$destination"
      log "Copied compose file from local checkout: $local_compose"
      return 0
    fi
  fi

  log "Downloading compose file from: $NEXUS_PLUS_COMPOSE_URL"
  curl -fsSL "$NEXUS_PLUS_COMPOSE_URL" -o "${destination}.tmp"
  mv "${destination}.tmp" "$destination"
}

wait_for_health() {
  local health_url=$1
  local deadline attempt body
  deadline=$((SECONDS + NEXUS_PLUS_WAIT_TIMEOUT))
  attempt=0

  while (( SECONDS < deadline )); do
    attempt=$((attempt + 1))
    body="$(curl -fsS -m 3 "$health_url" 2>/dev/null || true)"
    if [[ "$body" == *'"status":"UP"'* ]]; then
      log "Health check is UP: $body"
      return 0
    fi
    if (( attempt == 1 || attempt % 5 == 0 )); then
      log "Waiting for service readiness at $health_url ..."
    fi
    sleep 2
  done

  warn "Health check did not become UP within ${NEXUS_PLUS_WAIT_TIMEOUT}s."
  warn "Container status:"
  "${COMPOSE_CMD[@]}" -f "$COMPOSE_FILE" ps >&2 || true
  warn "Recent nexus-plus logs:"
  "${COMPOSE_CMD[@]}" -f "$COMPOSE_FILE" logs --tail 160 nexus-plus >&2 || true
  return 1
}

WORKDIR="${NEXUS_PLUS_DIR:-nexus-plus-quickstart}"
COMPOSE_FILE="${NEXUS_PLUS_COMPOSE_FILE:-docker-compose.quickstart.yml}"
NEXUS_PLUS_COMPOSE_URL="${NEXUS_PLUS_COMPOSE_URL:-https://raw.githubusercontent.com/klboke/nexus-plus/main/docker-compose.quickstart.yml}"
NEXUS_PLUS_WAIT_TIMEOUT="${NEXUS_PLUS_WAIT_TIMEOUT:-180}"
NEXUS_PLUS_SKIP_PORT_CHECK="${NEXUS_PLUS_SKIP_PORT_CHECK:-false}"
SCRIPT_SOURCE="${BASH_SOURCE[0]:-}"
SCRIPT_DIR=""
if [[ -n "$SCRIPT_SOURCE" && "$SCRIPT_SOURCE" != /dev/fd/* && -f "$SCRIPT_SOURCE" ]]; then
  SCRIPT_DIR="$(cd "$(dirname "$SCRIPT_SOURCE")" && pwd -P)"
fi

step "Checking local prerequisites"
command_exists docker || fail "Docker is required. Install Docker Desktop first."
command_exists curl || fail "curl is required to download files and run health checks."
docker info >/dev/null 2>&1 || fail "Docker is not running or is not reachable."
resolve_compose_command
log "Docker is available."
log "Compose command: $COMPOSE_DISPLAY"

step "Preparing quickstart directory"
mkdir -p "$WORKDIR"
cd "$WORKDIR"
log "Working directory: $(pwd)"

if [[ -f .env ]]; then
  load_env_file_defaults .env
fi

: "${NEXUS_PLUS_IMAGE_TAG:=0.1.0}"
: "${NEXUS_PLUS_HTTP_PORT:=19090}"
: "${NEXUS_PLUS_MANAGEMENT_PORT:=19091}"
: "${NEXUS_PLUS_PROJECT_NAME:=nexus-plus-quickstart}"
export \
  NEXUS_PLUS_IMAGE_TAG \
  NEXUS_PLUS_HTTP_PORT \
  NEXUS_PLUS_MANAGEMENT_PORT \
  NEXUS_PLUS_PROJECT_NAME \
  NEXUS_PLUS_CREDENTIAL_SECRET \
  NEXUS_PLUS_API_KEY_PAYLOAD_SECRET

create_env_file_if_missing .env

step "Fetching Docker Compose quickstart file"
download_compose_file "$COMPOSE_FILE"
COMPOSE_READY=true
log "Compose file: $(pwd)/$COMPOSE_FILE"
log "Image tag: ghcr.io/klboke/nexus-plus:${NEXUS_PLUS_IMAGE_TAG}"
log "HTTP port: ${NEXUS_PLUS_HTTP_PORT}"
log "Management port: ${NEXUS_PLUS_MANAGEMENT_PORT}"

step "Checking host port availability"
existing_container="$("${COMPOSE_CMD[@]}" -f "$COMPOSE_FILE" ps -q nexus-plus 2>/dev/null || true)"
if [[ -n "$existing_container" ]]; then
  log "Existing quickstart container detected; Compose will reconcile it."
elif [[ "$NEXUS_PLUS_SKIP_PORT_CHECK" == "true" ]]; then
  warn "Port check skipped because NEXUS_PLUS_SKIP_PORT_CHECK=true."
else
  port_in_use "$NEXUS_PLUS_HTTP_PORT" && fail "Port ${NEXUS_PLUS_HTTP_PORT} is already in use. Set NEXUS_PLUS_HTTP_PORT to another port and retry."
  port_in_use "$NEXUS_PLUS_MANAGEMENT_PORT" && fail "Port ${NEXUS_PLUS_MANAGEMENT_PORT} is already in use. Set NEXUS_PLUS_MANAGEMENT_PORT to another port and retry."
  log "Ports are available."
fi

step "Pulling container images"
"${COMPOSE_CMD[@]}" -f "$COMPOSE_FILE" pull

step "Starting nexus-plus"
"${COMPOSE_CMD[@]}" -f "$COMPOSE_FILE" up -d
"${COMPOSE_CMD[@]}" -f "$COMPOSE_FILE" ps

step "Waiting for nexus-plus readiness"
wait_for_health "http://127.0.0.1:${NEXUS_PLUS_MANAGEMENT_PORT}/actuator/health"

cat <<EOF

[nexus-plus quickstart] Ready.

Open:
  Admin console : http://127.0.0.1:${NEXUS_PLUS_HTTP_PORT}/admin/
  User browser  : http://127.0.0.1:${NEXUS_PLUS_HTTP_PORT}/browse/
  Health check  : http://127.0.0.1:${NEXUS_PLUS_MANAGEMENT_PORT}/actuator/health
  Prometheus    : http://127.0.0.1:${NEXUS_PLUS_MANAGEMENT_PORT}/actuator/prometheus

First visit:
  Create the initial Local/admin administrator password in the UI.
  Then create a blob store named "default". File storage is fine for this local trial.

Useful commands:
  cd $(pwd)
  ${COMPOSE_DISPLAY} -f ${COMPOSE_FILE} ps
  ${COMPOSE_DISPLAY} -f ${COMPOSE_FILE} logs -f nexus-plus
  ${COMPOSE_DISPLAY} -f ${COMPOSE_FILE} down
  ${COMPOSE_DISPLAY} -f ${COMPOSE_FILE} down -v

EOF
