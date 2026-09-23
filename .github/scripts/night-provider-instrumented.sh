#!/usr/bin/env bash
set -euo pipefail

extension_apk="$(find night-animepahe-extension-apk -type f -iname '*.apk' -print -quit)"
if [ -z "$extension_apk" ]; then
  echo "AnimePahe APK is missing from the downloaded workflow artifact." >&2
  find night-animepahe-extension-apk -maxdepth 5 -type f -print >&2 || true
  exit 1
fi

adb install --no-streaming -r "$extension_apk"
cd whatsapp-ai-android
chmod +x gradlew
if ./gradlew :app:connectedDebugAndroidTest \
  -Dorg.gradle.jvmargs="-Xmx4096m -XX:MaxMetaspaceSize=1024m -Dfile.encoding=UTF-8" \
  --max-workers=1 \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.whatsapp.data.night.NightAiGatewayProviderInstrumentedTest,com.example.whatsapp.data.night.NightChatHistoryInstrumentedTest,com.example.whatsapp.data.night.NightSpeechServiceInstrumentedTest,com.example.whatsapp.data.night.NightAppearanceControllerInstrumentedTest,com.example.whatsapp.data.night.NightLibraryIntegrationInstrumentedTest,com.example.whatsapp.data.night.NightProviderAdminInstrumentedTest,com.example.whatsapp.extensions.tools.NightMcpHttpBridgeInstrumentedTest,com.example.whatsapp.extensions.tools.NightExtensionToolIntegrationInstrumentedTest \
  --stacktrace; then
  exit 0
else
  mkdir -p ../night-provider-diagnostics
  adb logcat -d -v time -s NightExtension:I NightAnimePahe:I AndroidRuntime:E \
    > ../night-provider-diagnostics/provider-failure-logcat.txt || true
  cat ../night-provider-diagnostics/provider-failure-logcat.txt
  exit 1
fi
