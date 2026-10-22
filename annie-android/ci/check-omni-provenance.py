#!/usr/bin/env python3
"""Static upstream-origin and GPL notice guard; not an emulator/build verdict."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
repo = root.parent
omni = root / "third_party/omni-browser"
src = root / "app/src/main/java/com/tomex777/annie/browser/engine"
assets = root / "app/src/main/assets"

def check(condition, why):
    if not condition:
        raise SystemExit(f"OMNI PROVENANCE FAIL: {why}")

manifest = (omni / "README.md").read_text(encoding="utf-8")
readme = (repo / "README.md").read_text(encoding="utf-8")
about = (root / "app/src/main/java/com/tomex777/annie/AnnieAbout.kt").read_text(encoding="utf-8")
gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
upstream = "https://github.com/REBEL-ROOT/omni-browser"
check(upstream in manifest and "9de84cc0d4c7cce32c7052e6d7a8ceab4bdc6d16" in manifest,
      "pinned upstream source SHA missing")
check(upstream in readme.splitlines()[:12].__str__(), "upstream link not prominently in root README")
check(upstream in about and "Based on Omni Browser by RebelRoot" in about,
      "mandatory visible in-app credit or clickable origin missing")
check((omni / "LICENSE").exists() and (omni / "TRADEMARK.md").exists(),
      "original GPL and trademark policy missing")
check("GNU GENERAL PUBLIC LICENSE" in (omni / "LICENSE").read_text(encoding="utf-8"),
      "GNU GPL text missing from retained upstream license")
check("GNU GENERAL PUBLIC LICENSE" in (assets / "licenses/omni-GPL-3.0.txt").read_text(encoding="utf-8"),
      "bundled in-app GPL license removed")
check('applicationId = "com.tomex777.annie"' in gradle, "Annie package identity changed")
check("minSdk = 26" in gradle and '"arm64-v8a"' in gradle, "Android 8/ARM64 contract changed")
check("com.rebelroot.omni" not in gradle, "original browser package ID reused")

ported = (
    "AnnieSessionState.kt",
    "SessionStatePersistence.kt",
    "extensions/BuiltInExtensionManager.kt",
    "useragent/UserAgentManager.kt",
    "tabs/SmartTabManager.kt",
    "permissions/PermissionPrompts.kt",
    "permissions/SitePermission.kt",
    "bookmarks/model/OmniBookmark.kt",
    "bookmarks/model/BookmarkNode.kt",
    "bookmarks/model/BookmarkCollection.kt",
    "bookmarks/model/BookmarkTreeQueries.kt",
    "bookmarks/storage/BookmarkStorage.kt",
    "bookmarks/parser/NetscapeBookmarkParser.kt",
    "bookmarks/export/NetscapeBookmarkExporter.kt",
    "AnnieBrowserFindController.kt",
)
for relative in ported:
    path = src / relative
    check(path.exists(), f"ported upstream source absent: {relative}")
    text = path.read_text(encoding="utf-8")
    check("RebelRoot" in text, f"upstream copyright notice missing: {relative}")
    check(str(path.relative_to(repo)) in manifest,
          f"source path absent from pinned provenance manifest: {relative}")
    check("package com.rebelroot.omni" not in text,
          f"unmodified upstream package identity in Annie binary: {relative}")

copy = (assets / "web_extensions/annie_universal_copy/manifest.json").read_text(encoding="utf-8")
check("Annie Universal Copy" in copy and "omni-universal-copy@" not in copy,
      "upstream trademark in bundled extension identity")
model = (root / "app/src/main/java/com/tomex777/annie/AnnieGeckoBrowser.kt").read_text(encoding="utf-8")
check("AnnieBrowserEngineBridge" in model and "needsRestoration" in model,
      "real Omni-derived backend integration or lazy Gecko restoration removed")
check("selectedId != tab.id" in model and "prompt.dismiss()" in model,
      "background tab file chooser consent gate removed")
check("No source code is copied from Omni Browser" not in model,
      "false origin disclaimer has returned")
print("OMNI PROVENANCE PASS: pinned source/headers, GPL + credits, rebrand and engine guards.")
