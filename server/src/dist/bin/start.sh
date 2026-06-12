#!/usr/bin/env bash
set -euo pipefail

BIN_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEFAULT_APP_HOME="$(cd "$BIN_DIR/.." && pwd)"
APP_HOME="${NEXUS_PLUS_HOME:-$DEFAULT_APP_HOME}"
CONF_DIR="${NEXUS_PLUS_CONF_DIR:-$APP_HOME/conf}"
LOG_DIR="${NEXUS_PLUS_LOG_DIR:-$APP_HOME/logs}"
PID_FILE="${NEXUS_PLUS_PID_FILE:-$LOG_DIR/nexus-plus.pid}"
CONSOLE_LOG="${NEXUS_PLUS_CONSOLE_LOG:-$LOG_DIR/console.log}"
JAR_FILE="${NEXUS_PLUS_JAR_FILE:-$APP_HOME/lib/nexus-plus.jar}"
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"

is_running() {
  local pid="${1:-}"
  [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null
}

mkdir -p "$LOG_DIR" "$APP_HOME/data"

if [[ ! -f "$JAR_FILE" ]]; then
  echo "[start] missing jar: $JAR_FILE" >&2
  exit 1
fi

if [[ ! -f "$CONF_DIR/application.properties" ]]; then
  echo "[start] missing config: $CONF_DIR/application.properties" >&2
  exit 1
fi

if [[ -f "$PID_FILE" ]]; then
  PID="$(tr -dc '0-9' <"$PID_FILE" || true)"
  if is_running "$PID"; then
    echo "[start] already running, pid=$PID"
    exit 0
  fi
  echo "[start] removing stale PID file: $PID_FILE"
  rm -f "$PID_FILE"
fi

export NEXUS_PLUS_HOME="$APP_HOME"

COMMAND=("$JAVA_BIN")
if [[ -n "${JAVA_OPTS:-}" ]]; then
  # shellcheck disable=SC2206
  COMMAND+=($JAVA_OPTS)
fi
COMMAND+=(
  -jar "$JAR_FILE"
  "--spring.config.additional-location=optional:file:$CONF_DIR/"
)

echo "[start] starting nexus-plus"
echo "[start] home=$APP_HOME"
echo "[start] config=$CONF_DIR/application.properties"
echo "[start] log=$CONSOLE_LOG"

nohup "${COMMAND[@]}" >>"$CONSOLE_LOG" 2>&1 &
PID="$!"
echo "$PID" >"$PID_FILE"

echo "[start] pid=$PID"
echo "[start] status: $BIN_DIR/status.sh"
