#!/usr/bin/env python3
"""Reapply Torri's identity on top of Mihon v0.21.2 without replacing its engine.

Fails closed when expected upstream text moves. Keeps the upstream performance,
download, database, extension, and Cloudflare implementations intact.
"""
from pathlib import Path
import re


def transform(path, fn):
    p = Path(path)
    before = p.read_text(encoding="utf-8")
    after = fn(before)
    if before != after:
        p.write_text(after, encoding="utf-8")


def replace_one(value, old, new):
    if old == new:
        return value
    if old not in value:
        if new in value:
            return value
        raise RuntimeError(f"Expected upstream anchor is missing: {old[:100]!r}")
    return value.replace(old, new, 1)


transform("app/build.gradle.kts", lambda s: replace_one(s, 'applicationId = "app.mihon"', 'applicationId = "app.torri"'))
transform("settings.gradle.kts", lambda s: replace_one(s, 'rootProject.name = "Mihon"', 'rootProject.name = "Torri"'))


def strings(s):
    s, count = re.subn(r'(<string name="app_name"[^>]*>)[^<]*(</string>)', r'\g<1>Torri\g<2>', s, count=1)
    if count != 1:
        raise RuntimeError("app_name not found in i18n")
    if '<string name="theme_night">' not in s:
        m = re.search(r'^(\s*<string name="theme_tokyonight">[^\n]*\n)', s, flags=re.MULTILINE)
        if not m:
            raise RuntimeError("Tokyo Night theme string anchor missing")
        s = s[:m.end()] + '    <string name="theme_night">Night</string>\n' + s[m.end():]
    return s

transform("i18n/src/commonMain/moko-resources/base/strings.xml", strings)

transform(
    "app/src/main/java/eu/kanade/domain/ui/model/AppTheme.kt",
    lambda s: s if "    NIGHT(MR.strings.theme_night)," in s else replace_one(s, "    MONOCHROME(MR.strings.theme_monochrome),", "    MONOCHROME(MR.strings.theme_monochrome),\n    NIGHT(MR.strings.theme_night),"),
)


def prefs(s):
    before = """        if (DeviceUtil.isDynamicColorAvailable) {
            AppTheme.MONET
        } else {
            AppTheme.DEFAULT
        },"""
    return replace_one(s, before, "        AppTheme.NIGHT,")

transform("app/src/main/java/eu/kanade/domain/ui/UiPreferences.kt", prefs)


def theme(s):
    if "import eu.kanade.presentation.theme.colorscheme.NightColorScheme" not in s:
        s = replace_one(
            s,
            "import eu.kanade.presentation.theme.colorscheme.MonochromeColorScheme",
            "import eu.kanade.presentation.theme.colorscheme.MonochromeColorScheme\nimport eu.kanade.presentation.theme.colorscheme.NightColorScheme",
        )
    return s if "    AppTheme.NIGHT to NightColorScheme," in s else replace_one(
        s,
        "    AppTheme.MONOCHROME to MonochromeColorScheme,",
        "    AppTheme.MONOCHROME to MonochromeColorScheme,\n    AppTheme.NIGHT to NightColorScheme,",
    )

transform("app/src/main/java/eu/kanade/presentation/theme/TachiyomiTheme.kt", theme)
transform(
    "app/src/main/java/eu/kanade/presentation/more/LogoHeader.kt",
    lambda s: replace_one(s, "R.drawable.ic_mihon", "R.drawable.ic_torri"),
)

# Keep Torri's branding visible on the existing new-version About screen.
def about(s):
    if 'title = "Torri by Night"' in s:
        return s
    anchor = """                item {
                    TextPreferenceWidget(
                        title = stringResource(MR.strings.version),"""
    brand = """                item {
                    TextPreferenceWidget(
                        title = "Torri by Night",
                        subtitle = "Your gate into manga · Built on Mihon",
                        onPreferenceClick = { uriHandler.openUri("https://github.com/Tomex777/Torri") },
                    )
                }

"""
    return replace_one(s, anchor, brand + anchor)

transform("app/src/main/java/eu/kanade/presentation/more/settings/screen/about/AboutScreen.kt", about)

assert 'applicationId = "app.torri"' in Path("app/build.gradle.kts").read_text()
assert 'versionName = "0.21.2"' in Path("app/build.gradle.kts").read_text()
assert 'rootProject.name = "Torri"' in Path("settings.gradle.kts").read_text()
print("Torri identity and Night theme port applied to v0.21.2.")
