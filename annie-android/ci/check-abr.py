#!/usr/bin/env python3
"""Fail Annie CI when agreed ABR constraints regress in core UI surfaces."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = root / "app/src/main/java/com/tomex777/annie"


def check(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"ABR FAIL: {message}")


gradle = (root / "app/build.gradle.kts").read_text()
check("minSdk = 26" in gradle, "Android 8 minimum was changed")
check('"arm64-v8a"' in gradle, "ARM64 build target was removed")
check("isUniversalApk = true" in gradle, "universal APK artifact was removed")

# The package ID is pinned for existing installs. A com.nice.annie migration must
# be a separately verified data/signing migration, not a silent rename.
check('applicationId = "com.tomex777.annie"' in gradle,
      "the existing-app upgrade path changed without an explicit migration")

for file in ("HostAiSettings.kt", "ScriptStudio.kt", "AnnieGeckoBrowser.kt",
             "MainActivity.kt", "AnnieBrowserUi.kt"):
    contents = (source / file).read_text()
    check("OutlinedTextField(" not in contents, f"stock material input in {file}")
    check("TextField(" not in contents.replace("BasicTextField(", ""),
          f"stock text input in {file}")

common = (source / "AnnieSettingsInput.kt").read_text()
check("BasicTextField(" in common and "PasswordVisualTransformation()" in common,
      "native-styled, masked input has been removed")
chat = (source / "MainActivity.kt").read_text()
check("drawer_downloads" not in chat and 'testTag("library_downloads")' in chat,
      "duplicate downloads navigation route returned")
learning = (source / "AnnieScriptLearning.kt").read_text()
check("script_learning_create_footer" not in learning,
      "duplicate create-script CTA returned")

for file in ("AnnieCanvasMessage.kt", "AnnieImageViewerActivity.kt",
             "AnnieImageEditorActivity.kt"):
    contents = (source / file).read_text()
    check("WindowInsetsCompat.Type.statusBars()" in contents,
          f"fullscreen status bar not hidden: {file}")
    check(".statusBarsPadding()" not in contents,
          f"fullscreen content reserves a hidden status bar: {file}")

canvas = (source / "AnnieCanvasMessage.kt").read_text()
check("Interactive • offline sandbox" not in canvas,
      "Canvas regained filler copy")

print("ABR core UI contract PASS: native inputs, route deduplication, "
      "Android 8/ARM64, upgrade identity and immersive fullscreen")
