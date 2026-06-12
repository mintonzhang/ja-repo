#!/usr/bin/env bash
set -euo pipefail

BIN_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEFAULT_APP_HOME="$(cd "$BIN_DIR/.." && pwd)"
APP_HOME="${NEXUS_PLUS_HOME:-$DEFAULT_APP_HOME}"
LOG_DIR="${NEXUS_PLUS_LOG_DIR:-$APP_HOME/logs}"
PID_FILE="${NEXUS_PLUS_PID_FILE:-$LOG_DIR/nexus-plus.pid}"
STOP_TIMEOUT_SECONDS="${NEXUS_PLUS_STOP_TIMEOUT_SECONDS:-30}"

is_running() {
  local pid="${1:-}"
  [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null
}

if [[ ! -f "$PID_FILE" ]]; then
  echo "[stop] no PID file at $PID_FILE"
  exit 0
fi

PID="$(tr -dc '0-9' <"$PID_FILE" || true)"
if ! is_running "$PID"; then
  echo "[stop] process is not running, removing stale PID file"
  rm -f "$PID_FILE"
  exit 0
fi

echo "[stop] sending TERM to pid=$PID"
kill "$PID" 2>/dev/null || true

for ((i = 1; i <= STOP_TIMEOUT_SECONDS; i++)); do
  if ! is_running "$PID"; then
    rm -f "$PID_FILE"
    echo "[stop] stopped"
    exit 0
  fi
  sleep 1
done

echo "[stop] still running after ${STOP_TIMEOUT_SECONDS}s, sending KILL to pid=$PID"
kill -9 "$PID" 2>/dev/null || true
rm -f "$PID_FILE"
echo "[stop] stopped"
