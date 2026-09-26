#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS="$HOME/.sora-tools"
ANDROID_HOME="$HOME/android-sdk"
GRADLE_HOME="$TOOLS/gradle-9.5.0"

mkdir -p "$TOOLS" "$ANDROID_HOME/cmdline-tools"

if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "Installing Android command-line tools..."
  rm -rf /tmp/sora-android-cli /tmp/sora-android-cli.zip
  wget -q "https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip" -O /tmp/sora-android-cli.zip
  mkdir -p /tmp/sora-android-cli
  unzip -q /tmp/sora-android-cli.zip -d /tmp/sora-android-cli
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv /tmp/sora-android-cli/cmdline-tools "$ANDROID_HOME/cmdline-tools/latest"
fi

export ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$GRADLE_HOME/bin:$PATH"

yes | sdkmanager --licenses >/dev/null || true
sdkmanager "platform-tools" "platforms;android-37" "build-tools;37.0.0"

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  echo "Installing Gradle 9.5.0..."
  rm -f /tmp/gradle-9.5.0-bin.zip
  wget -q "https://services.gradle.org/distributions/gradle-9.5.0-bin.zip" -O /tmp/gradle-9.5.0-bin.zip
  rm -rf "$GRADLE_HOME"
  unzip -q /tmp/gradle-9.5.0-bin.zip -d "$TOOLS"
fi

ENV_BLOCK=$(cat <<'EOF'
# Sora Android Codespaces tools
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_HOME="$HOME/.sora-tools/gradle-9.5.0"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$GRADLE_HOME/bin:$PATH"
EOF
)

if ! grep -q "Sora Android Codespaces tools" "$HOME/.bashrc" 2>/dev/null; then
  printf '\n%s\n' "$ENV_BLOCK" >> "$HOME/.bashrc"
fi

mkdir -p "$ROOT/sora-ci/workspace"
rm -rf "$ROOT/sora-ci/workspace/Sora_Android_v0_1"
cat "$ROOT"/sora-ci/chunks/part-*.txt | base64 -d > /tmp/sora-android.zip
unzip -q /tmp/sora-android.zip -d "$ROOT/sora-ci/workspace"

echo "Android SDK: $ANDROID_HOME"
java -version
gradle --version

echo "Sora source reconstructed at: $ROOT/sora-ci/workspace/Sora_Android_v0_1"
