#!/usr/bin/env bash
set -euo pipefail

API_DIR="$HOME/.m2/repository/io/github/libxposed/api/100"
INTERFACE_DIR="$HOME/.m2/repository/io/github/libxposed/interface/100"
SERVICE_DIR="$HOME/.m2/repository/io/github/libxposed/service/100-1.0.0"
SERVICE_ROOT="libxposed/service"
# The upstream libxposed/service ref `100` disappeared in September 2026. This is the
# preserved upstream API-100 commit (LoveSy, 2023-10-09) from a public fork retaining
# the original commit history. It publishes io.github.libxposed:service:100-1.0.0,
# targets Java 17, and declares IXposedService.API = 100.
SERVICE_REPOSITORY="fakepepsilol/libxposed.service"
SERVICE_COMMIT="4351a735755c86c031a977a62e52005b23048c4d"

rm -rf "$SERVICE_ROOT"
mkdir -p "$API_DIR" "$INTERFACE_DIR" "$SERVICE_DIR" libxposed

# The preserved API-100 service compiles against Android 34 / Build Tools 34.0.0.
# Keep this prerequisite with the dependency preparation itself so every CI consumer
# (build, security and target-UI) resolves the same exact historical contract.
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/usr/local/lib/android/sdk}}"
if [[ ! -d "$SDK_ROOT/platforms/android-34" || ! -d "$SDK_ROOT/build-tools/34.0.0" ]]; then
  SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
  test -x "$SDKMANAGER"
  yes | "$SDKMANAGER" --licenses >/dev/null || true
  "$SDKMANAGER" "platforms;android-34" "build-tools;34.0.0"
fi

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

# Build only the binary AARs. The historical publication also attaches Dokka-generated
# Javadoc jars; that documentation task is broken on current GitHub runners and is not
# needed by Night Core.
(
  cd "$SERVICE_ROOT"
  ./gradlew --no-daemon :interface:bundleReleaseAar :service:bundleReleaseAar
)

INTERFACE_AAR="$(find "$SERVICE_ROOT/interface/build/outputs/aar" -maxdepth 1 -type f -name '*release.aar' -print -quit)"
SERVICE_AAR="$(find "$SERVICE_ROOT/service/build/outputs/aar" -maxdepth 1 -type f -name '*release.aar' -print -quit)"
test -n "$INTERFACE_AAR"
test -n "$SERVICE_AAR"

# API-100's historical interface and service Android libraries both declare the namespace
# io.github.libxposed.service. AGP 9 rejects two Android libraries with the same namespace.
# The interface module only contributes generated Binder classes, so expose its exact
# classes.jar as a plain Maven JAR. This preserves the API-100 Binder implementation while
# keeping only the service AAR's Android manifest/provider in Night Core.
unzip -p "$INTERFACE_AAR" classes.jar > "$INTERFACE_DIR/interface-100.jar"
cp "$SERVICE_AAR" "$SERVICE_DIR/service-100-1.0.0.aar"

test -s "$INTERFACE_DIR/interface-100.jar"

cat > "$INTERFACE_DIR/interface-100.pom" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.github.libxposed</groupId>
  <artifactId>interface</artifactId>
  <version>100</version>
  <packaging>jar</packaging>
</project>
POM

cat > "$SERVICE_DIR/service-100-1.0.0.pom" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.github.libxposed</groupId>
  <artifactId>service</artifactId>
  <version>100-1.0.0</version>
  <packaging>aar</packaging>
  <dependencies>
    <dependency>
      <groupId>io.github.libxposed</groupId>
      <artifactId>interface</artifactId>
      <version>100</version>
      <scope>compile</scope>
    </dependency>
  </dependencies>
</project>
POM

# Fail early unless every exact API-100 coordinate needed by Night Core is installed.
test -f "$API_DIR/api-100.aar"
test -f "$API_DIR/api-100.pom"
test -f "$INTERFACE_DIR/interface-100.jar"
test -f "$INTERFACE_DIR/interface-100.pom"
test -f "$SERVICE_DIR/service-100-1.0.0.aar"
test -f "$SERVICE_DIR/service-100-1.0.0.pom"
