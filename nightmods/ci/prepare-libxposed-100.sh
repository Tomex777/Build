#!/usr/bin/env bash
set -euo pipefail

API_DIR="$HOME/.m2/repository/io/github/libxposed/api/100"
SERVICE_ROOT="libxposed/service"

rm -rf "$SERVICE_ROOT"
mkdir -p "$API_DIR" libxposed

# Match LSPosed ET's own build: install its pinned API-100 artifact verbatim instead of
# resolving whatever the current libxposed/api repository happens to publish.
curl --fail --location --retry 3 \
  https://raw.githubusercontent.com/EzequielDevTeam/LSPosed-ET/master/ci/libxposed-api-100/api-100.aar \
  --output "$API_DIR/api-100.aar"
curl --fail --location --retry 3 \
  https://raw.githubusercontent.com/EzequielDevTeam/LSPosed-ET/master/ci/libxposed-api-100/api-100.pom \
  --output "$API_DIR/api-100.pom"

# Resolve ref "100" the same way actions/checkout does (branch OR tag) while avoiding the
# repository-scoped credential installed by the primary checkout.
curl --fail --location --retry 3 \
  https://api.github.com/repos/libxposed/service/tarball/100 \
  --output /tmp/libxposed-service-100.tar.gz
mkdir -p "$SERVICE_ROOT"
tar -xzf /tmp/libxposed-service-100.tar.gz --strip-components=1 -C "$SERVICE_ROOT"

(
  cd "$SERVICE_ROOT"
  ./gradlew --no-daemon publishToMavenLocal
)

# Fail early if either exact API-100 coordinate was installed/published.
test -f "$API_DIR/api-100.aar"
test -f "$API_DIR/api-100.pom"
test -d "$HOME/.m2/repository/io/github/libxposed/service/100-1.0.0"
