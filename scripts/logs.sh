#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_FILE="$ROOT/logs/server.log"

if [[ ! -f "$LOG_FILE" ]]; then
  echo "No log file at $LOG_FILE. Run scripts/dev.sh first."
  exit 1
fi

exec tail -F "$LOG_FILE"
