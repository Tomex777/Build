#!/usr/bin/env python3
"""Fail API26/API36 runs that skipped any Agent 1 Gecko test classes."""
import pathlib
import sys
import xml.etree.ElementTree as ET

REQUIRED = {
    "com.tomex777.annie.AnnieOmniNativeHomeTest",
    "com.tomex777.annie.AnnieGeckoTabRestoreTest",
    "com.tomex777.annie.AnnieOmniExtensionTest",
    "com.tomex777.annie.AnnieOmniPersistenceSecurityTest",
    "com.tomex777.annie.AnnieOmniUserAgentTest",
    "com.tomex777.annie.AnnieOmniSmartTabsTest",
    "com.tomex777.annie.AnnieOmniFindInPageTest",
    "com.tomex777.annie.AnnieOmniSitePermissionTest",
    "com.tomex777.annie.browser.engine.bookmarks.storage.AnnieOmniBookmarkStorageTest",
    "com.tomex777.annie.AnnieOmniBookmarkImportBridgeTest",
    "com.tomex777.annie.AnnieOmniIncomingIntentTest",
    "com.tomex777.annie.AnnieOmniSessionRestoreTest",
    "com.tomex777.annie.AnnieGeckoBrowserEndToEndTest",
    "com.tomex777.annie.AnnieGeckoBrowserUiTest",
}
seen = set()
failures = []
test_count = 0
for file in pathlib.Path(sys.argv[1]).rglob("*.xml"):
    try:
        doc = ET.parse(file).getroot()
    except ET.ParseError:
        continue
    for case in doc.iter("testcase"):
        name = case.get("classname", "")
        if name in REQUIRED:
            seen.add(name)
            test_count += 1
            if any(case.find(tag) is not None for tag in ("failure", "error", "skipped")):
                failures.append(f"{name}.{case.get('name')}")
missing = REQUIRED - seen
print(f"Omni native tests seen: {test_count}; classes covered: {len(seen)}/{len(REQUIRED)}")
if missing or failures:
    print(f"FAIL missing classes: {sorted(missing)}; failed/skipped: {sorted(failures)}")
    sys.exit(1)
print("Omni engine instrumentation PASS — all mandatory classes were executed.")
