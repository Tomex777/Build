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


def replace_string_resource(path: Path, name: str, value: str) -> bool:
    text = path.read_text(encoding="utf-8")
    pattern = rf'(<string\s+name="{re.escape(name)}"[^>]*>)(.*?)(</string>)'
    out, count = re.subn(pattern, rf'\1{value}\3', text, count=1, flags=re.S)
    if count:
        path.write_text(out, encoding="utf-8")
        return True
    return False


def ensure_string_resource(path: Path, name: str, value: str) -> None:
    text = path.read_text(encoding="utf-8")
    pattern = rf'<string\s+name="{re.escape(name)}"[^>]*>.*?</string>'
    replacement = f'<string name="{name}">{value}</string>'
    if re.search(pattern, text, flags=re.S):
        text = re.sub(pattern, replacement, text, count=1, flags=re.S)
    else:
        text = text.replace("</resources>", f"    {replacement}\n</resources>")
    path.write_text(text, encoding="utf-8")


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: apply_nightmods.py /path/to/LSPosed-ET")

    repo = Path(sys.argv[1]).resolve()
    main_nav = repo / "app/src/main/res/navigation/main_nav.xml"
    modules_nav = repo / "app/src/main/res/navigation/modules_nav.xml"
    nav_menu = repo / "app/src/main/res/menu/navigation_menu.xml"
    strings = repo / "app/src/main/res/values/strings.xml"
    strings_untranslatable = repo / "app/src/main/res/values/strings_untranslatable.xml"
    main_activity = repo / "app/src/main/java/org/lsposed/manager/ui/activity/MainActivity.java"
    modules_fragment = repo / "app/src/main/java/org/lsposed/manager/ui/fragment/ModulesFragment.java"
    overlay = Path(__file__).parent / "overlay"

    for p in (
        main_nav,
        modules_nav,
        nav_menu,
        strings,
        strings_untranslatable,
        main_activity,
        modules_fragment,
        repo / "app/src/main/java/org/lsposed/manager/ConfigManager.java",
    ):
        require(p)

    text = main_nav.read_text(encoding="utf-8")
    text = replace_once(
        text,
        r'app:startDestination="@id/main_fragment"',
        'app:startDestination="@id/modules_nav"',
        "module-first start destination",
    )
    main_nav.write_text(text, encoding="utf-8")

    # Keep the proven LSPosed scope destination/action, but replace only the
    # visible module-list fragment with Night's own native presentation.
    modules_nav_text = modules_nav.read_text(encoding="utf-8")
    modules_nav_text = replace_once(
        modules_nav_text,
        r'android:name="org\.lsposed\.manager\.ui\.fragment\.ModulesFragment"',
        'android:name="org.lsposed.manager.nightmods.ui.NightModulesFragment"',
        "Night-owned Modules fragment",
    )
    modules_nav.write_text(modules_nav_text, encoding="utf-8")

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

    # LSPosed ET keeps the actual launcher label in strings_untranslatable.xml.
    # Update that exact resource rather than merely adding a second unused label.
    if not replace_string_resource(strings_untranslatable, "app_name", "Night Mods"):
        raise SystemExit("Could not find app_name in strings_untranslatable.xml")

    # Keep any optional full-name resource synchronized if upstream adds/uses it.
    replace_string_resource(strings, "app_name_full", "Night Mods")
    ensure_string_resource(strings, "night_framework_active", "Framework active")
    ensure_string_resource(strings, "night_framework_unavailable", "Framework unavailable")
    ensure_string_resource(strings, "night_framework_connected_detail", "Xposed service connected")
    ensure_string_resource(strings, "night_framework_unavailable_detail", "Module controls require the Xposed framework service.")
    ensure_string_resource(strings, "night_modules_empty_title", "No modules installed")
    ensure_string_resource(strings, "night_modules_empty_detail", "Installed Xposed modules will appear here.")
    ensure_string_resource(strings, "night_modules_offline_title", "Modules unavailable")
    ensure_string_resource(strings, "night_modules_offline_detail", "Start the framework service to load and manage modules.")
    ensure_string_resource(strings, "night_module_toggle_failed", "Couldn’t change the module state.")

    # Upstream removes most bottom-navigation destinations when the daemon is not
    # connected. Night Mods keeps the complete application shell visible instead.
    # Destinations that genuinely require the daemon/Magisk stay visible but disabled.
    activity_text = main_activity.read_text(encoding="utf-8")
    activity_text = replace_once(
        activity_text,
        r'''\s*if \(!ConfigManager\.isBinderAlive\(\)\) \{\s*nav\.getMenu\(\)\.removeItem\(R\.id\.logs_fragment\);\s*nav\.getMenu\(\)\.removeItem\(R\.id\.modules_nav\);\s*if \(!ConfigManager\.isMagiskInstalled\(\)\) \{\s*nav\.getMenu\(\)\.removeItem\(R\.id\.repo_nav\);\s*\}\s*\}''',
        '''\n            var modulesItem = nav.getMenu().findItem(R.id.modules_nav);\n            var logsItem = nav.getMenu().findItem(R.id.logs_fragment);\n            var repoItem = nav.getMenu().findItem(R.id.repo_nav);\n            if (modulesItem != null) modulesItem.setEnabled(true);\n            if (logsItem != null) logsItem.setEnabled(ConfigManager.isBinderAlive());\n            if (repoItem != null) repoItem.setEnabled(ConfigManager.isMagiskInstalled());''',
        "persistent Night Mods navigation",
    )
    main_activity.write_text(activity_text, encoding="utf-8")

    # Keep the old fragment safe as a fallback/deep-link target while the Night-owned
    # screen is introduced. This also fixes any legacy route that still reaches it.
    modules_text = modules_fragment.read_text(encoding="utf-8")
    modules_text = replace_once(
        modules_text,
        r'''private void updateModuleSummary\(\) \{\s*var moduleCount = moduleUtil\.getEnabledModulesCount\(\);\s*runOnUiThread\(\(\) -> \{\s*if \(binding != null\) \{\s*binding\.toolbar\.setSubtitle\(moduleCount == -1 \? getString\(R\.string\.loading\) : getResources\(\)\.getQuantityString\(R\.plurals\.modules_enabled_count, moduleCount, moduleCount\)\);\s*binding\.toolbarLayout\.setSubtitle\(binding\.toolbar\.getSubtitle\(\)\);\s*\}\s*\}\);\s*\}''',
        '''private void updateModuleSummary() {\n        var moduleCount = moduleUtil.getEnabledModulesCount();\n        var binderAlive = ConfigManager.isBinderAlive();\n        runOnUiThread(() -> {\n            if (binding != null) {\n                if (!binderAlive) {\n                    binding.toolbar.setSubtitle(R.string.night_framework_unavailable);\n                    binding.fab.hide();\n                } else {\n                    binding.toolbar.setSubtitle(moduleCount == -1 ? getString(R.string.loading) : getResources().getQuantityString(R.plurals.modules_enabled_count, moduleCount, moduleCount));\n                    showFab();\n                }\n                binding.toolbarLayout.setSubtitle(binding.toolbar.getSubtitle());\n            }\n        });\n    }''',
        "framework-aware module summary",
    )
    modules_fragment.write_text(modules_text, encoding="utf-8")

    for src in overlay.rglob("*"):
        if not src.is_file():
            continue
        rel = src.relative_to(overlay)
        dst = repo / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)

    print("Night Mods overlay applied successfully.")
    print("Preserved manager applicationId/package for LSPosed ET Binder compatibility.")
    print("Launcher label: Night Mods.")
    print("Night-owned Modules screen: enabled.")
    print("Primary navigation stays visible even when the framework is unavailable.")
    print("Primary navigation: Modules -> Repository -> Logs -> Settings.")


if __name__ == "__main__":
    main()
