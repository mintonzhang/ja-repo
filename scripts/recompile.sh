#!/usr/bin/env bash
# Trigger incremental Java compile -> writes new .class files into target/classes,
# which spring-boot-devtools picks up and restarts the Spring context (~2-5s)
# without a full JVM restart.
#
# Run this in a 2nd terminal whenever you change Java code.
# Use `-l` to loop: re-compile every time you press Enter.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

compile_once() {
  echo "[recompile] $(date '+%H:%M:%S') compiling..."
  if mvn -pl server -am compile -q; then
    echo "[recompile] done (devtools should restart Spring context)"
  else
    echo "[recompile] FAILED — keep watching the server log for context"
  fi
}

if [[ "${1:-}" == "-l" || "${1:-}" == "--loop" ]]; then
  while true; do
    compile_once
    read -r -p "[recompile] press Enter to rebuild, Ctrl-C to quit > " _
  done
else
  compile_once
fi
