#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PROJECT_VERSION="$(mvn -q -N -DforceStdout help:evaluate -Dexpression=project.version)"
DIST_VERSION="${PROJECT_VERSION%-SNAPSHOT}"

echo "[dist] project version: $PROJECT_VERSION"
echo "[dist] archive version: $DIST_VERSION"

echo "[dist] building Spring Boot executable jar and installing reactor artifacts..."
mvn -pl server -am -DskipTests install spring-boot:repackage

echo "[dist] assembling archive distribution..."
mvn -pl server -DskipTests -Dnexus.plus.dist.version="$DIST_VERSION" assembly:single

echo "[dist] built archives:"
ls -lh "server/target/nexus-plus-${DIST_VERSION}.tar.gz" "server/target/nexus-plus-${DIST_VERSION}.zip"
