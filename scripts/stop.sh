#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$ROOT/logs"
PID_FILE="$LOG_DIR/server.pid"
PORT="${NEXUS_PLUS_PORT:-18090}"

mkdir -p "$LOG_DIR"

is_running() {
  local pid="${1:-}"
  [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null
}

command_for_pid() {
  ps -o command= -p "$1" 2>/dev/null || true
}

is_repo_process() {
  local command
  command="$(command_for_pid "$1")"
  [[ "$command" == *"$ROOT"* || "$command" == *"NexusPlusApplication"* ]]
}

port_pids() {
  lsof -nP -tiTCP:"$PORT" -sTCP:LISTEN 2>/dev/null | awk 'NF && !seen[$0]++'
}

repo_process_pids() {
  ps -axo pid=,command= | awk -v root="$ROOT" '
    index($0, root) && (index($0, "spring-boot:run") || index($0, "NexusPlusApplication")) { print $1 }
  '
}

parent_pid() {
  ps -o ppid= -p "$1" 2>/dev/null | tr -d ' ' || true
}

child_pids() {
  pgrep -P "$1" 2>/dev/null || true
}

add_pid() {
  local pid="${1:-}"
  if is_running "$pid" && [[ "$pid" != "$$" && "$pid" != "1" ]]; then
    TARGETS+=("$pid")
  fi
}

add_tree() {
  local pid="${1:-}"
  local child
  add_pid "$pid"
  while IFS= read -r child; do
    add_tree "$child"
  done < <(child_pids "$pid")
}

add_repo_parent() {
  local pid="${1:-}"
  local parent
  parent="$(parent_pid "$pid")"
  if is_running "$parent" && is_repo_process "$parent"; then
    add_pid "$parent"
  fi
}

unique_pids() {
  printf '%s\n' "$@" | awk 'NF && !seen[$0]++'
}

process_group_for() {
  ps -o pgid= -p "$1" 2>/dev/null | tr -d ' ' || true
}

send_signal() {
  local signal="$1"
  shift
  local pid pgid
  local -a groups=()
  local -a individuals=()

  for pid in "$@"; do
    if ! is_running "$pid"; then
      continue
    fi
    pgid="$(process_group_for "$pid")"
    if [[ -n "$pgid" && "$pgid" == "$pid" ]]; then
      groups+=("$pgid")
    else
      individuals+=("$pid")
    fi
  done

  if [[ "${#groups[@]}" -gt 0 ]]; then
    while IFS= read -r pgid; do
      [[ -n "$pgid" ]] && kill "-$signal" "-$pgid" 2>/dev/null || true
    done < <(unique_pids "${groups[@]}")
  fi

  if [[ "${#individuals[@]}" -gt 0 ]]; then
    while IFS= read -r pid; do
      [[ -n "$pid" ]] && kill "-$signal" "$pid" 2>/dev/null || true
    done < <(unique_pids "${individuals[@]}")
  fi
}

wait_until_stopped() {
  local pid
  for _ in {1..20}; do
    local any_running=0
    for pid in "$@"; do
      if is_running "$pid"; then
        any_running=1
        break
      fi
    done

    if [[ "$any_running" == "0" && -z "$(port_pids || true)" ]]; then
      return 0
    fi
    sleep 0.5
  done
  return 1
}

declare -a TARGETS=()

if [[ -f "$PID_FILE" ]]; then
  PID="$(tr -dc '0-9' <"$PID_FILE" || true)"
  if is_running "$PID" && is_repo_process "$PID"; then
    echo "[stop] found PID file pid=$PID"
    add_tree "$PID"
    add_repo_parent "$PID"
  else
    echo "[stop] pid file is stale or not nexus-plus (${PID:-empty}); removing $PID_FILE"
    rm -f "$PID_FILE"
  fi
else
  echo "[stop] no PID file at $PID_FILE; checking repo processes and port $PORT."
fi

while IFS= read -r pid; do
  add_tree "$pid"
  add_repo_parent "$pid"
done < <(repo_process_pids || true)

while IFS= read -r pid; do
  add_tree "$pid"
  add_repo_parent "$pid"
done < <(port_pids || true)

declare -a DEDUPED_TARGETS=()
if [[ "${#TARGETS[@]}" -gt 0 ]]; then
  while IFS= read -r pid; do
    DEDUPED_TARGETS+=("$pid")
  done < <(unique_pids "${TARGETS[@]}")
fi
if [[ "${#DEDUPED_TARGETS[@]}" -gt 0 ]]; then
  TARGETS=("${DEDUPED_TARGETS[@]}")
else
  TARGETS=()
fi

if [[ "${#TARGETS[@]}" -eq 0 ]]; then
  rm -f "$PID_FILE"
  echo "[stop] nothing running on port $PORT. Done."
  exit 0
fi

echo "[stop] sending TERM to pid(s): ${TARGETS[*]}"
send_signal TERM "${TARGETS[@]}"

if ! wait_until_stopped "${TARGETS[@]}"; then
  while IFS= read -r pid; do
    add_tree "$pid"
  done < <(repo_process_pids || true)
  while IFS= read -r pid; do
    add_tree "$pid"
  done < <(port_pids || true)
  DEDUPED_TARGETS=()
  while IFS= read -r pid; do
    DEDUPED_TARGETS+=("$pid")
  done < <(unique_pids "${TARGETS[@]}")
  if [[ "${#DEDUPED_TARGETS[@]}" -gt 0 ]]; then
    TARGETS=("${DEDUPED_TARGETS[@]}")
  else
    TARGETS=()
  fi
  echo "[stop] still running/listening on $PORT; sending KILL to pid(s): ${TARGETS[*]}"
  send_signal KILL "${TARGETS[@]}"
fi

rm -f "$PID_FILE"
echo "[stop] done."
