#!/usr/bin/env bash
set -euo pipefail

BIN_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEFAULT_APP_HOME="$(cd "$BIN_DIR/.." && pwd)"
APP_HOME="${NEXUS_PLUS_HOME:-$DEFAULT_APP_HOME}"
CONF_DIR="${NEXUS_PLUS_CONF_DIR:-$APP_HOME/conf}"
LOG_DIR="${NEXUS_PLUS_LOG_DIR:-$APP_HOME/logs}"
PID_FILE="${NEXUS_PLUS_PID_FILE:-$LOG_DIR/nexus-plus.pid}"

is_running() {
  local pid="${1:-}"
  [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null
}

config_value() {
  local key="$1"
  local file="$CONF_DIR/application.properties"
  if [[ -f "$file" ]]; then
    awk -F= -v k="$key" '
      $0 !~ /^[[:space:]]*#/ && $1 == k {
        value=$0
        sub(/^[^=]*=/, "", value)
        print value
        exit
      }
    ' "$file"
  fi
}

if [[ ! -f "$PID_FILE" ]]; then
  echo "[status] stopped (no PID file)"
  exit 3
fi

PID="$(tr -dc '0-9' <"$PID_FILE" || true)"
if ! is_running "$PID"; then
  echo "[status] stopped (stale PID file: $PID_FILE)"
  exit 3
fi

echo "[status] running, pid=$PID"

if command -v curl >/dev/null 2>&1; then
  MANAGEMENT_PORT="${NEXUS_PLUS_MANAGEMENT_PORT:-$(config_value management.server.port)}"
  MANAGEMENT_PORT="${MANAGEMENT_PORT:-8081}"
  HEALTH_URL="${NEXUS_PLUS_HEALTH_URL:-http://127.0.0.1:$MANAGEMENT_PORT/actuator/health}"
  if curl -fsS "$HEALTH_URL" >/dev/null 2>&1; then
    echo "[status] health=UP ($HEALTH_URL)"
  else
    echo "[status] health=UNKNOWN ($HEALTH_URL)"
  fi
fi
