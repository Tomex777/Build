#!/usr/bin/env python3
"""Fast, dependency-free guard for NIGHT Browser integration PRs.

This checks verifiable project wiring and dangerous Android manifest regressions.
It is NOT a substitute for compiling, installing, or interacting with GeckoView.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
BLOCKED_PERMISSIONS = {
    "android.permission.QUERY_ALL_PACKAGES",
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.REQUEST_INSTALL_PACKAGES",
    "android.permission.READ_SMS",
    "android.permission.READ_CALL_LOG",
}


def inspect_browser(project: Path) -> list[str]:
    """Return actionable errors; use a branch-neutral, consistent package ID."""
    failures: list[str] = []
    app = project / "app"
    gradle = app / "build.gradle.kts"
    manifest = app / "src/main/AndroidManifest.xml"
    if not gradle.is_file() or not manifest.is_file():
        return ["Android app Gradle file or AndroidManifest.xml is missing"]

    build = gradle.read_text(encoding="utf-8")
    def value(name: str) -> str | None:
        match = re.search(r"\b" + re.escape(name) + r'\s*=\s*"([^"]+)"', build)
        return match.group(1) if match else None

    namespace, app_id = value("namespace"), value("applicationId")
    if not namespace or not app_id:
        failures.append("Declare a literal namespace and applicationId in app/build.gradle.kts")
    elif namespace != app_id:
        failures.append(f"Namespace {namespace} and applicationId {app_id} differ")
    elif not re.fullmatch(r"com\.(night|nice)\.browser", app_id):
        failures.append(f"Unexpected browser ID {app_id}; obtain project owner approval")

    sdk_match = re.search(r"\bminSdk\s*=\s*(\d+)", build)
    if not sdk_match or int(sdk_match.group(1)) != 26:
        failures.append("Preserve minSdk 26 until the owner changes ABR")
    if "org.mozilla.geckoview:geckoview" not in build:
        failures.append("The real GeckoView runtime dependency is missing")

    try:
        xml = ET.parse(manifest).getroot()
    except ET.ParseError as exc:
        return failures + [f"Invalid AndroidManifest.xml: {exc}"]

    application = xml.find("application")
    if application is None:
        return failures + ["No <application> in AndroidManifest.xml"]
    for key in ("allowBackup", "usesCleartextTraffic"):
        if application.get(ANDROID + key) != "false":
            failures.append(f"AndroidManifest application must set android:{key}=\"false\"")

    providers = application.findall("provider")
    file_providers = [p for p in providers
                      if p.get(ANDROID + "name") == "androidx.core.content.FileProvider"]
    if len(file_providers) != 1:
        failures.append("Exactly one AndroidX FileProvider is required for PDF handoff")
    elif app_id:
        provider = file_providers[0]
        if provider.get(ANDROID + "authorities") != app_id + ".files":
            failures.append("FileProvider authority does not match applicationId + '.files'")
        if provider.get(ANDROID + "exported") != "false":
            failures.append("FileProvider must not be exported")

    for p in xml.findall("uses-permission"):
        permission = p.get(ANDROID + "name", "")
        if permission in BLOCKED_PERMISSIONS:
            failures.append(f"High-risk permission requires explicit security review: {permission}")

    activity = application.find("activity")
    if activity is None:
        failures.append("No launcher activity declared")
    elif activity.get(ANDROID + "name") not in (".MainActivity", (namespace or "") + ".MainActivity"):
        failures.append("Expected native MainActivity entry point")

    for scope in ("main", "test"):
        source = app / f"src/{scope}/java"
        if not source.is_dir():
            continue
        for kt in source.rglob("*.kt"):
            text = kt.read_text(encoding="utf-8")
            match = re.search(r"(?m)^package\s+([\w.]+)", text)
            actual = match.group(1) if match else None
            if not actual or not namespace or (
                actual != namespace and not actual.startswith(namespace + ".")
            ):
                failures.append(f"{kt.relative_to(project)} has a foreign/missing Kotlin package: {actual}")
    return failures


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    errors = inspect_browser(args.project)
    if errors:
        for error in errors:
            print(f"FAIL: {error}")
        return 1
    print("PASS: NIGHT Browser integration wiring, SDK and manifest security invariants")
    print("NOTE: Build, emulator/device install, real-site interaction and privacy QA remain mandatory")
    return 0


if __name__ == "__main__":
    sys.exit(main())
