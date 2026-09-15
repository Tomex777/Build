#!/usr/bin/env python3
"""Apply the Night Mods presentation/integration layer to an LSPosed ET checkout."""
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


def ensure_plurals_resource(path: Path, name: str, one: str, other: str) -> None:
    text = path.read_text(encoding="utf-8")
    pattern = rf'<plurals\s+name="{re.escape(name)}"[^>]*>.*?</plurals>'
    replacement = f'<plurals name="{name}">\n        <item quantity="one">{one}</item>\n        <item quantity="other">{other}</item>\n    </plurals>'
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
    manager_manifest = repo / "app/src/main/AndroidManifest.xml"
    main_activity = repo / "app/src/main/java/org/lsposed/manager/ui/activity/MainActivity.java"
    modules_fragment = repo / "app/src/main/java/org/lsposed/manager/ui/fragment/ModulesFragment.java"
    overlay = Path(__file__).parent / "overlay"

    for p in (main_nav, modules_nav, nav_menu, strings, strings_untranslatable, manager_manifest,
              main_activity, modules_fragment, repo / "app/src/main/java/org/lsposed/manager/ConfigManager.java"):
        require(p)

    text = main_nav.read_text(encoding="utf-8")
    text = replace_once(text, r'app:startDestination="@id/main_fragment"', 'app:startDestination="@id/modules_nav"', "module-first start destination")
    main_nav.write_text(text, encoding="utf-8")

    nav_menu.write_text('''<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@+id/modules_nav" android:icon="@drawable/ic_extension_checkable" android:title="@string/Modules" />
    <item android:id="@+id/repo_nav" android:icon="@drawable/ic_get_app_checkable" android:title="@string/module_repo" />
    <item android:id="@+id/logs_fragment" android:icon="@drawable/ic_assignment_checkable" android:title="@string/Logs" />
    <item android:id="@+id/settings_fragment" android:icon="@drawable/ic_settings_checkable" android:title="@string/Settings" />
</menu>
''', encoding="utf-8")

    if not replace_string_resource(strings_untranslatable, "app_name", "Night Mods"):
        raise SystemExit("Could not find app_name in strings_untranslatable.xml")
    replace_string_resource(strings, "app_name_full", "Night Mods")

    night_strings = {
        "night_framework_active": "Framework active",
        "night_framework_unavailable": "Framework unavailable",
        "night_framework_connected_detail": "Xposed service connected",
        "night_framework_unavailable_detail": "Framework controls are unavailable, but Night capabilities can still be configured.",
        "night_module_toggle_failed": "Couldn’t change the module state.",
        "night_section": "Night",
        "night_core": "Night Core",
        "night_engine": "Engine",
        "night_xposed": "Xposed",
        "night_module_state": "Module",
        "night_module_enabled": "Enabled",
        "night_module_disabled": "Disabled",
        "night_scope_unavailable": "Unavailable while the framework is offline",
        "night_core_not_recognized": "Installed, but not recognized by the framework",
        "night_capabilities": "Capabilities",
        "night_bubble_styler": "Bubble Styler",
        "night_bubble_targets_summary": "WhatsApp · Instagram",
        "night_installed": "Installed",
        "night_installed_version": "Installed · %1$s",
        "night_core_not_installed": "Not installed",
        "night_scopes": "Scopes",
        "night_enabled": "Enabled",
        "night_targets": "Targets",
        "night_shape": "Shape",
        "night_layout": "Layout",
        "night_core_connected": "Night Core connected",
        "night_core_unavailable": "Night Core unavailable. Install or update Night Core to configure this capability.",
        "night_core_write_failed": "Couldn’t save Night Core settings.",
        "night_corner_radius_value": "Corner radius · %1$d",
        "night_message_spacing_value": "Message spacing · %1$d",
        "night_restart_targets_note": "Restart the target app after changing these settings so the hook reloads the updated configuration.",
        "night_no_other_modules": "No other modules installed",
        "night_no_other_modules_detail": "Third-party Xposed modules will appear here.",
        "night_core_configure": "Installed · Configure in Night Mods",
        "night_core_configure_version": "Installed · %1$s · Configure in Night Mods",
        "night_core_framework_summary": "%1$s · %2$s",
        "night_core_framework_summary_version": "%1$s · %2$s · %3$s",
    }
    for name, value in night_strings.items():
        ensure_string_resource(strings, name, value)
    ensure_plurals_resource(strings, "night_target_count", "%1$d target", "%1$d targets")

    manifest_text = manager_manifest.read_text(encoding="utf-8")
    if 'android:name="dev.nightmods.core"' not in manifest_text:
        manifest_text = replace_once(manifest_text, r'(\s*<application\b)', '\n    <queries>\n        <package android:name="dev.nightmods.core" />\n        <provider android:authorities="dev.nightmods.core.settings" />\n    </queries>\n\n    <application', "Night Core package visibility")
        manager_manifest.write_text(manifest_text, encoding="utf-8")

    activity_text = main_activity.read_text(encoding="utf-8")
    activity_text = replace_once(activity_text,
        r'''\s*if \(!ConfigManager\.isBinderAlive\(\)\) \{\s*nav\.getMenu\(\)\.removeItem\(R\.id\.logs_fragment\);\s*nav\.getMenu\(\)\.removeItem\(R\.id\.modules_nav\);\s*if \(!ConfigManager\.isMagiskInstalled\(\)\) \{\s*nav\.getMenu\(\)\.removeItem\(R\.id\.repo_nav\);\s*\}\s*\}''',
        '''\n            var modulesItem = nav.getMenu().findItem(R.id.modules_nav);\n            var logsItem = nav.getMenu().findItem(R.id.logs_fragment);\n            var repoItem = nav.getMenu().findItem(R.id.repo_nav);\n            if (modulesItem != null) modulesItem.setEnabled(true);\n            if (logsItem != null) logsItem.setEnabled(ConfigManager.isBinderAlive());\n            if (repoItem != null) repoItem.setEnabled(ConfigManager.isMagiskInstalled());''',
        "persistent Night Mods navigation")
    main_activity.write_text(activity_text, encoding="utf-8")

    modules_text = modules_fragment.read_text(encoding="utf-8")
    modules_text = replace_once(modules_text,
        r'''private void updateModuleSummary\(\) \{\s*var moduleCount = moduleUtil\.getEnabledModulesCount\(\);\s*runOnUiThread\(\(\) -> \{\s*if \(binding != null\) \{\s*binding\.toolbar\.setSubtitle\(moduleCount == -1 \? getString\(R\.string\.loading\) : getResources\(\)\.getQuantityString\(R\.plurals\.modules_enabled_count, moduleCount, moduleCount\)\);\s*binding\.toolbarLayout\.setSubtitle\(binding\.toolbar\.getSubtitle\(\)\);\s*\}\s*\}\);\s*\}''',
        '''private void updateModuleSummary() {\n        var moduleCount = moduleUtil.getEnabledModulesCount();\n        var binderAlive = ConfigManager.isBinderAlive();\n        runOnUiThread(() -> {\n            if (binding != null) {\n                if (!binderAlive) {\n                    binding.toolbar.setSubtitle(R.string.night_framework_unavailable);\n                    binding.fab.hide();\n                } else {\n                    binding.toolbar.setSubtitle(moduleCount == -1 ? getString(R.string.loading) : getResources().getQuantityString(R.plurals.modules_enabled_count, moduleCount, moduleCount));\n                    showFab();\n                }\n                binding.toolbarLayout.setSubtitle(binding.toolbar.getSubtitle());\n            }\n        });\n    }''',
        "framework-aware module summary")
    modules_text = replace_once(modules_text,
        r'''safeNavigate\(ModulesFragmentDirections\.actionModulesFragmentToAppListFragment\(item\.packageName, item\.userId\)\);''',
        '''Bundle args = new Bundle();\n                    args.putString("modulePackageName", item.packageName);\n                    args.putInt("moduleUserId", item.userId);\n                    try {\n                        getNavController().navigate(R.id.action_modules_fragment_to_app_list_fragment, args);\n                    } catch (IllegalArgumentException ignored) {\n                    }''',
        "fallback ModulesFragment navigation without Safe Args class")
    modules_fragment.write_text(modules_text, encoding="utf-8")

    for src in overlay.rglob("*"):
        if not src.is_file():
            continue
        rel = src.relative_to(overlay)
        dst = repo / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)

    print("Night Mods overlay applied successfully.")
    print("Preserved LSPosed ET Binder/service plumbing and manager package identity.")
    print("Night Core first-party flow: Modules -> Night Core -> Bubble Styler.")
    print("Primary navigation: Modules -> Repository -> Logs -> Settings.")


if __name__ == "__main__":
    main()
