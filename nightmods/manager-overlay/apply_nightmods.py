#!/usr/bin/env python3
"""Apply the Night Mods manager overlay to an LSPosed ET checkout.

Conservative by design: it preserves the original package/application ID and all
framework IPC. It changes branding/navigation and adds a small integration helper.
"""
from __future__ import annotations
import re
import shutil
import sys
from pathlib import Path


def require(path: Path) -> None:
    if not path.exists():
        raise SystemExit(f"Missing expected LSPosed ET path: {path}")


def replace_once(text: str, pattern: str, replacement: str, description: str) -> str:
    out, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Could not apply {description}; expected exactly one match, got {count}")
    return out


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: apply_nightmods.py /path/to/LSPosed-ET")

    repo = Path(sys.argv[1]).resolve()
    main_nav = repo / "app/src/main/res/navigation/main_nav.xml"
    nav_menu = repo / "app/src/main/res/menu/navigation_menu.xml"
    strings = repo / "app/src/main/res/values/strings.xml"
    overlay = Path(__file__).parent / "overlay"

    for p in (main_nav, nav_menu, strings, repo / "app/src/main/java/org/lsposed/manager/ConfigManager.java"):
        require(p)

    text = main_nav.read_text(encoding="utf-8")
    text = replace_once(
        text,
        r'app:startDestination="@id/main_fragment"',
        'app:startDestination="@id/modules_nav"',
        "module-first start destination",
    )
    main_nav.write_text(text, encoding="utf-8")

    menu = '''<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:id="@+id/modules_nav"
        android:icon="@drawable/ic_extension_checkable"
        android:title="@string/Modules" />
    <item
        android:id="@+id/repo_nav"
        android:icon="@drawable/ic_get_app_checkable"
        android:title="@string/module_repo" />
    <item
        android:id="@+id/logs_fragment"
        android:icon="@drawable/ic_assignment_checkable"
        android:title="@string/Logs" />
    <item
        android:id="@+id/settings_fragment"
        android:icon="@drawable/ic_settings_checkable"
        android:title="@string/Settings" />
</menu>
'''
    nav_menu.write_text(menu, encoding="utf-8")

    s = strings.read_text(encoding="utf-8")
    app_name_patterns = [
        r'(<string\s+name="app_name"[^>]*>)(.*?)(</string>)',
        r'(<string\s+name="app_name_full"[^>]*>)(.*?)(</string>)',
    ]
    changed = False
    for pattern in app_name_patterns:
        if re.search(pattern, s, flags=re.S):
            s = re.sub(pattern, r'\1Night Mods\3', s, count=1, flags=re.S)
            changed = True
    if not changed:
        insertion = '\n    <string name="night_mods_name">Night Mods</string>\n'
        s = s.replace('</resources>', insertion + '</resources>')
    strings.write_text(s, encoding="utf-8")

    for src in overlay.rglob("*"):
        if not src.is_file():
            continue
        rel = src.relative_to(overlay)
        dst = repo / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)

    print("Night Mods overlay applied successfully.")
    print("Preserved manager applicationId/package for LSPosed ET Binder compatibility.")
    print("Primary navigation: Modules -> Repository -> Logs -> Settings.")


if __name__ == "__main__":
    main()
