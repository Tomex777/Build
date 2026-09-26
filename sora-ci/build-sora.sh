#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT="$ROOT/sora-ci/workspace/Sora_Android_v0_1"
ARTIFACTS="$ROOT/sora-ci/artifacts"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export GRADLE_HOME="${GRADLE_HOME:-$HOME/.sora-tools/gradle-9.5.0}"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$GRADLE_HOME/bin:$PATH"

if [ ! -f "$PROJECT/settings.gradle.kts" ]; then
  echo "Sora source is missing. Run: bash sora-ci/codespace-setup.sh" >&2
  exit 1
fi

rm -rf "$ARTIFACTS" /tmp/sora-core-isolation
mkdir -p "$ARTIFACTS"

printf '\n=== 1/2: Core isolation build ===\n'
cp -R "$PROJECT" /tmp/sora-core-isolation
rm -rf /tmp/sora-core-isolation/test-extension
sed -i '/include(":test-extension")/d' /tmp/sora-core-isolation/settings.gradle.kts

test ! -d /tmp/sora-core-isolation/test-extension
if grep -R "com.night.sora.ext.demo\|DemoExtensionService\|DemoCatalog" /tmp/sora-core-isolation/app /tmp/sora-core-isolation/extension-api; then
  echo "ERROR: Core contains a demo-extension implementation reference." >&2
  exit 1
fi

(
  cd /tmp/sora-core-isolation
  gradle --stacktrace :app:assembleDebug
)
cp /tmp/sora-core-isolation/app/build/outputs/apk/debug/app-debug.apk "$ARTIFACTS/Sora-Core-debug.apk"

printf '\n=== 2/2: External extension build ===\n'
(
  cd "$PROJECT"
  gradle --stacktrace :test-extension:assembleDebug
)
cp "$PROJECT/test-extension/build/outputs/apk/debug/test-extension-debug.apk" "$ARTIFACTS/Sora-Test-Extension-debug.apk"

printf '\n=== Build complete ===\n'
ls -lh "$ARTIFACTS"
sha256sum "$ARTIFACTS"/*.apk | tee "$ARTIFACTS/SHA256SUMS.txt"
echo "Core was compiled with test-extension physically absent."
