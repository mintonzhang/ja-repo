#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

NEXUS_HOME="${NEXUS_HOME:-/private/tmp/nexus-3292-source/nexus-base-template-3.29.2-02}"
NEXUS_COMPAT_PORT="${NEXUS_COMPAT_PORT:-58083}"
NEXUS_PLUS_COMPAT_BASE_URL="${NEXUS_PLUS_COMPAT_BASE_URL:-http://127.0.0.1:18090}"
NEXUS_COMPAT_DATA_DIR="${NEXUS_COMPAT_DATA_DIR:-$(mktemp -d /private/tmp/nexus-plus-compat-nexus.XXXXXX)}"
NEXUS_COMPAT_LOG="$NEXUS_COMPAT_DATA_DIR/log/nexus-stdout.log"
NEXUS_COMPAT_BASE_URL="http://127.0.0.1:$NEXUS_COMPAT_PORT"
START_TIMEOUT_SECONDS="${NEXUS_COMPAT_START_TIMEOUT_SECONDS:-150}"

if [[ ! -x "$NEXUS_HOME/bin/nexus" ]]; then
  echo "[compat] Nexus launcher not found or not executable: $NEXUS_HOME/bin/nexus" >&2
  exit 1
fi

if lsof -nP -iTCP:"$NEXUS_COMPAT_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "[compat] port $NEXUS_COMPAT_PORT is already in use; set NEXUS_COMPAT_PORT to another port." >&2
  exit 1
fi

if ! curl -m 5 -fsS "$NEXUS_PLUS_COMPAT_BASE_URL/actuator/health" >/dev/null; then
  echo "[compat] nexus-plus is not healthy at $NEXUS_PLUS_COMPAT_BASE_URL; start it before running this script." >&2
  exit 1
fi

mkdir -p "$NEXUS_COMPAT_DATA_DIR/etc" "$NEXUS_COMPAT_DATA_DIR/log"
cat >"$NEXUS_COMPAT_DATA_DIR/etc/nexus.properties" <<EOF
application-port=$NEXUS_COMPAT_PORT
application-host=127.0.0.1
nexus-context-path=/
EOF

stop_nexus() {
  if [[ "${nexus_pid:-}" =~ ^[0-9]+$ ]] && kill -0 "$nexus_pid" 2>/dev/null; then
    echo "[compat] stopping Nexus pid=$nexus_pid"
    kill -TERM "$nexus_pid" 2>/dev/null || true
    (
      sleep 30
      kill -KILL "$nexus_pid" 2>/dev/null || true
    ) &
    killer_pid=$!
    wait "$nexus_pid" 2>/dev/null || true
    kill "$killer_pid" 2>/dev/null || true
    wait "$killer_pid" 2>/dev/null || true
  fi
}
trap stop_nexus EXIT

echo "[compat] starting disposable Nexus 3.29.2 at $NEXUS_COMPAT_BASE_URL"
echo "[compat] data dir: $NEXUS_COMPAT_DATA_DIR"
(
  cd "$PROJECT_ROOT"
  KARAF_DATA="$NEXUS_COMPAT_DATA_DIR" \
  JAVA_MIN_MEM="${JAVA_MIN_MEM:-512m}" \
  JAVA_MAX_MEM="${JAVA_MAX_MEM:-1024m}" \
  DIRECT_MAX_MEM="${DIRECT_MAX_MEM:-1024m}" \
  "$NEXUS_HOME/bin/nexus" server
) >"$NEXUS_COMPAT_LOG" 2>&1 &
nexus_pid=$!

for ((i = 1; i <= START_TIMEOUT_SECONDS; i++)); do
  if [[ -f "$NEXUS_COMPAT_DATA_DIR/admin.password" ]] \
      && curl -m 5 -fsS "$NEXUS_COMPAT_BASE_URL/service/rest/v1/status" >/dev/null 2>&1; then
    break
  fi
  if ! kill -0 "$nexus_pid" 2>/dev/null; then
    echo "[compat] Nexus exited before it became ready." >&2
    tail -n 120 "$NEXUS_COMPAT_LOG" >&2 || true
    exit 1
  fi
  sleep 1
done

if [[ ! -f "$NEXUS_COMPAT_DATA_DIR/admin.password" ]]; then
  echo "[compat] timed out waiting for Nexus admin.password." >&2
  tail -n 120 "$NEXUS_COMPAT_LOG" >&2 || true
  exit 1
fi

if ! curl -m 5 -fsS "$NEXUS_COMPAT_BASE_URL/service/rest/v1/status" >/dev/null; then
  echo "[compat] timed out waiting for Nexus HTTP status endpoint." >&2
  tail -n 120 "$NEXUS_COMPAT_LOG" >&2 || true
  exit 1
fi

admin_password="$(cat "$NEXUS_COMPAT_DATA_DIR/admin.password")"
echo "[compat] Nexus is ready; running release and snapshot write compatibility tests."

(
  cd "$PROJECT_ROOT"
  NEXUS_COMPAT_BASE_URL="$NEXUS_COMPAT_BASE_URL" \
  NEXUS_COMPAT_USERNAME="admin" \
  NEXUS_COMPAT_PASSWORD="$admin_password" \
  NEXUS_PLUS_COMPAT_BASE_URL="$NEXUS_PLUS_COMPAT_BASE_URL" \
  COMPAT_WRITE_ENABLED="true" \
  mvn -pl compat-test -am \
    -DfailIfNoTests=false \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Dtest=MavenRepositoryBlackBoxCompatibilityTest#hostedReleaseDeployRoundTripMatchesNexusWhenConfigured+hostedSnapshotDeployRoundTripMatchesNexusWhenConfigured \
    test
)

echo "[compat] write compatibility passed."
