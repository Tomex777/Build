#!/usr/bin/env bash
set -euo pipefail

API_DIR="$HOME/.m2/repository/io/github/libxposed/api/100"
SERVICE_ROOT="libxposed/service"
# The upstream libxposed/service ref `100` disappeared in September 2026. This is the
# preserved upstream API-100 commit (LoveSy, 2023-10-09) from a public fork retaining
# the original commit history. It publishes io.github.libxposed:service:100-1.0.0,
# targets Java 17, and declares IXposedService.API = 100.
SERVICE_REPOSITORY="fakepepsilol/libxposed.service"
SERVICE_COMMIT="4351a735755c86c031a977a62e52005b23048c4d"

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

# Do not resolve a mutable/newer libxposed service ref. Fetch the preserved API-100 source
# by immutable commit so Night Core keeps the same service contract even if branches move.
curl --fail --location --retry 3 \
  "https://api.github.com/repos/${SERVICE_REPOSITORY}/tarball/${SERVICE_COMMIT}" \
  --output /tmp/libxposed-service-100.tar.gz
mkdir -p "$SERVICE_ROOT"
tar -xzf /tmp/libxposed-service-100.tar.gz --strip-components=1 -C "$SERVICE_ROOT"

# Guard against accidentally compiling a different libxposed service contract.
grep -q 'const int API = 100;' \
  "$SERVICE_ROOT/interface/src/main/aidl/io/github/libxposed/service/IXposedService.aidl"
grep -q 'version = "100-1.0.0"' "$SERVICE_ROOT/service/build.gradle.kts"
grep -q 'sourceCompatibility = JavaVersion.VERSION_17' "$SERVICE_ROOT/service/build.gradle.kts"
grep -q 'SharedPreferences getRemotePreferences' \
  "$SERVICE_ROOT/service/src/main/java/io/github/libxposed/service/XposedService.java"
grep -q 'void onServiceBind' \
  "$SERVICE_ROOT/service/src/main/java/io/github/libxposed/service/XposedServiceHelper.java"
grep -q 'void onServiceDied' \
  "$SERVICE_ROOT/service/src/main/java/io/github/libxposed/service/XposedServiceHelper.java"

(
  cd "$SERVICE_ROOT"
  ./gradlew --no-daemon publishToMavenLocal
)

# Fail early if either exact API-100 coordinate was installed/published.
test -f "$API_DIR/api-100.aar"
test -f "$API_DIR/api-100.pom"
test -d "$HOME/.m2/repository/io/github/libxposed/service/100-1.0.0"
