#!/usr/bin/env python3
"""Enforce production-facing Later UI cleanup after feature overlays."""
from pathlib import Path
import re
import sys
if len(sys.argv) != 2:
    raise SystemExit("usage: apply-production-ui-cleanup.py SOURCE_ROOT")
root = Path(sys.argv[1])
home = root / "app/src/main/java/com/night/later/ui/home/HomeScreen.kt"
h = home.read_text()
old = "Settings stays on Home."
new = "Appearance, privacy, and storage."
if h.count(old) != 1:
    raise SystemExit(f"expected one Home settings-copy anchor, found {h.count(old)}")
home.write_text(h.replace(old, new, 1))
settings = root / "app/src/main/java/com/night/later/ui/settings/SettingsScreen.kt"
s = settings.read_text()
for forbidden in (
    "Everything has a place now.",
    "Export encrypted archive",
    "What Later protects, and what an offline app cannot promise.",
    "AES-256-GCM",
    "Android Keystore",
    "block manifest",
    "ciphertext archive",
):
    if forbidden in s:
        raise SystemExit(f"production UI still exposes internal copy: {forbidden}")
if 'title =\n                                "Security"' in s:
    raise SystemExit("redundant Security-only settings row remains")
for required in ("Create backup", "Restore backup", "Manage storage and keep a recoverable backup."):
    if required not in s:
        raise SystemExit(f"missing production storage action: {required}")

# Fullscreen playback intentionally hides generated storage filenames; the media stays versioned internally.
viewer = root / "app/src/main/java/com/night/later/ui/media/LaterMediaViewer.kt"
v = viewer.read_text()
pattern = re.compile(
    r'(FullscreenVideoDialog\(\s*player\s*=\s*player,\s*displayName\s*=\s*)displayName\b',
    re.MULTILINE,
)
v, replaced = pattern.subn(r'\1""', v, count=1)
if replaced != 1:
    raise SystemExit(f"expected one fullscreen video filename anchor, found {replaced}")
viewer.write_text(v)

# TEMP: print the exact custom fullscreen footer source before production cleanup.
marker = "fun LaterVideoPlayerSurface"
idx = v.find(marker)
if idx < 0:
    raise SystemExit("LaterVideoPlayerSurface source marker not found")
print("LATER_VIDEO_SURFACE_DIAGNOSTIC_BEGIN")
print(v[idx:idx + 7000])
print("LATER_VIDEO_SURFACE_DIAGNOSTIC_END")
raise SystemExit("temporary Later video surface diagnostic")
