#!/usr/bin/env python3
"""NIGHT Browser Agent 3: real Android UI-driven tab/private/restart smoke.

Runs against an installed emulator and captures reproducible screenshots.
This does not replace human visual inspection or real login-cookie isolation tests.
"""
import re
import subprocess
import time
from pathlib import Path
from xml.etree import ElementTree as ET

OUT = Path("night-browser/screenshots/agent03")
OUT.mkdir(parents=True, exist_ok=True)


def adb(*args, check=True):
    return subprocess.run(["adb", *args], check=check, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout


def dump():
    adb("shell", "uiautomator", "dump", "/sdcard/agent03.xml")
    xml = adb("shell", "cat", "/sdcard/agent03.xml")
    (OUT / "last-ui.xml").write_text(xml)
    return ET.fromstring(xml)


def center(node):
    b = re.findall(r"\d+", node.get("bounds", ""))
    if len(b) != 4:
        raise AssertionError("UI node has no bounds: " + str(node.attrib))
    x1, y1, x2, y2 = map(int, b)
    return (x1 + x2) // 2, (y1 + y2) // 2


def tap(label, contains=False):
    for attempt in range(3):
        root = dump()
        for node in root.iter("node"):
            fields = (node.get("content-desc", ""), node.get("text", ""))
            if any((label in v if contains else label == v) for v in fields):
                x, y = center(node)
                adb("shell", "input", "tap", str(x), str(y))
                time.sleep(1.4)
                return
        time.sleep(1)
    raise AssertionError("Couldn't find tap target: " + label)


def screenshot(name):
    adb("shell", "screencap", "-p", "/sdcard/agent03.png")
    subprocess.run(["adb", "pull", "/sdcard/agent03.png", str(OUT / name)], check=True)


def site(domain):
    root = dump()
    input_node = next((n for n in root.iter("node")
                       if n.get("class", "").endswith("EditText")), None)
    if input_node is None:
        # BasicTextField may be exposed with placeholder text, not EditText class.
        input_node = next((n for n in root.iter("node")
                           if "Search or enter address" in (
                               n.get("text", "") + n.get("content-desc", ""))), None)
    if input_node is None:
        raise AssertionError("Address field inaccessible to UI test")
    x, y = center(input_node)
    adb("shell", "input", "tap", str(x), str(y))
    adb("shell", "input", "text", domain)
    tap("Go")
    time.sleep(9)


def count_profile(prefix):
    root = dump()
    found = []
    for n in root.iter("node"):
        value = n.get("text", "") + " " + n.get("content-desc", "")
        found += [int(x) for x in re.findall(r"\b" + prefix + r"\s+(\d+)\b", value)]
    if not found:
        raise AssertionError("Tab count not exposed for profile " + prefix)
    return max(found)


apk = Path("night-browser/app/build/outputs/apk/debug/app-x86_64-debug.apk")
assert apk.is_file(), "Emulator APK not downloaded"
adb("install", "-r", str(apk))
adb("shell", "wm", "size", "1080x1920")
adb("shell", "wm", "density", "420")
adb("shell", "pm", "clear", "com.night.browser")
adb("shell", "am", "start", "-n", "com.night.browser/.MainActivity")
time.sleep(14)
screenshot("01-start.png")

# Two actual regular websites in independent tabs.
site("example.com")
tap("Tabs")
tap("New tab")
site("example.org")
tap("Tabs")
assert count_profile("Regular") >= 2, "Normal tabs are not independently created"
screenshot("02-regular-tabs.png")

# Open private tab and navigate; it must not be stored by regular-tab persistence.
tap("Private", contains=True)
tap("New private tab")
site("mozilla.org")
tap("Tabs")
tap("Private", contains=True)
assert count_profile("Private") == 1, "Missing private tab"
screenshot("03-private-tab.png")

# Real process death/relaunch: normal tabs return, private tabs must not.
adb("shell", "am", "force-stop", "com.night.browser")
adb("shell", "am", "start", "-n", "com.night.browser/.MainActivity")
time.sleep(12)
tap("Tabs")
assert count_profile("Regular") >= 2, "Regular tab metadata was not restored"
tap("Private", contains=True)
assert count_profile("Private") == 0, "Private tabs leaked into persistent state"
screenshot("04-private-after-restart.png")

log = adb("logcat", "-d", "-t", "10000")
(OUT / "logcat.txt").write_text("\n".join(
    line for line in log.splitlines()
    if "AndroidRuntime" in line or "FATAL EXCEPTION" in line or "com.night.browser" in line
)[-15000:])
assert "FATAL EXCEPTION" not in (OUT / "logcat.txt").read_text(), "App crashed"
print("Agent 3 emulator tab creation/private/process restart smoke passed")
