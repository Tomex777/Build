#!/usr/bin/env python3
"""Remove implementation-oriented copy from Later's production UI."""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply-production-ui-cleanup.py SOURCE_ROOT")

root = Path(sys.argv[1])

replacements = {
    root / "app/src/main/java/com/night/later/ui/settings/SettingsScreen.kt": [
        (
            "Manage Later's local storage and create a ciphertext archive without revealing letter contents.",
            "Manage storage and export an encrypted archive."
        ),
        (
            "Encryption, privacy, and time-lock details",
            "Simple protection details for your capsules"
        ),
        (
            "What Later protects, and what an offline app cannot promise.",
            "How Later protects your capsules."
        ),
        (
            "Encrypted at rest",
            "Encrypted on device"
        ),
        (
            "Each capsule block uses AES-256-GCM with a random content key wrapped by an Android Keystore master key.",
            "Capsule contents are encrypted on this device."
        ),
        (
            "Encrypted metadata",
            "Private capsule details"
        ),
        (
            "Capsule timing and the block manifest are authenticated and encrypted too.",
            "Return time and capsule details are encrypted too."
        ),
        (
            "No plaintext backup",
            "Protected storage"
        ),
        (
            "Android backup is disabled. Temporary decrypted media lives in app cache and can be cleared from Storage & data.",
            "Temporary media can be cleared from Storage & data."
        ),
        (
            "Time-lock reality",
            "Time-lock protection"
        ),
        (
            "Later checks the return timestamp before decryption, but a fully offline app cannot provide a mathematical time lock against a rooted or instrumented device that ultimately owns the decryption capability.",
            "Later waits until the return time before opening a capsule. A compromised device can bypass any app-only time lock."
        ),
        (
            "Capsules and drafts stay encrypted in this archive. Opening the ZIP outside Later reveals ciphertext, not your letter contents. The current archive is device-bound because Later's master key stays in Android Keystore.",
            "Capsules and drafts stay encrypted in the exported archive. Its contents aren't readable outside Later, and the archive remains tied to this device."
        ),
    ],
    root / "app/src/main/java/com/night/later/ui/home/HomeScreen.kt": [
        (
            "Settings stays on Home.",
            "Appearance, privacy, and storage."
        ),
    ],
}

for path, pairs in replacements.items():
    text = path.read_text()
    for old, new in pairs:
        count = text.count(old)
        if count != 1:
            raise SystemExit(f"{path}: expected exactly one production-copy anchor {old!r}, found {count}")
        text = text.replace(old, new, 1)
    path.write_text(text)
