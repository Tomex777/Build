#!/usr/bin/env python3
"""Enforce production-facing Later UI cleanup after feature overlays."""
from pathlib import Path
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
