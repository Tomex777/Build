#!/usr/bin/env python3
"""Exercise actual Mihon tab UI on an Android emulator, not just settings logs.

The transparent reader chrome host must accept touches throughout modal
sheets, including positions outside the top and bottom toolbar hit areas.
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

REMOTE = "/sdcard/yomi-settings-touch-test.xml"
BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def adb(*args, timeout=50):
    return subprocess.run(["adb", *args], check=True, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, text=True, timeout=timeout)


def nodes():
    error = None
    for _ in range(4):
        try:
            adb("shell", "rm", "-f", REMOTE)
            adb("shell", "uiautomator", "dump", "--compressed", REMOTE)
            result = adb("exec-out", "cat", REMOTE)
            return list(ET.fromstring(result.stdout).iter("node"))
        except (ValueError, ET.ParseError, subprocess.SubprocessError) as exc:
            error = exc
            time.sleep(3)
    raise RuntimeError(f"Could not capture a live accessibility hierarchy: {error}")


def has_text(items, text):
    return any(item.attrib.get("text") == text or
               item.attrib.get("content-desc") == text for item in items)


def tap_label(label):
    for node in nodes():
        if node.attrib.get("text") != label and node.attrib.get("content-desc") != label:
            continue
        match = BOUNDS.fullmatch(node.attrib.get("bounds", ""))
        if match is None:
            continue
        left, top, right, bottom = map(int, match.groups())
        if right <= left or bottom <= top:
            continue
        adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
        return
    raise AssertionError(f"Couldn't locate a visible, tappable settings item: {label}")


def wait_for_content(label):
    for _ in range(5):
        if has_text(nodes(), label):
            return
        time.sleep(2)
    raise AssertionError(f"Tapped settings tab but expected content didn't render: {label}")


def main():
    initial = nodes()
    if not has_text(initial, "Reading mode") or not has_text(initial, "Color filter"):
        raise AssertionError("Reader settings tabs are not visible")
    tap_label("Color filter")
    wait_for_content("Custom brightness")
    tap_label("Reading mode")
    wait_for_content("For this series")
    print("PASS: Mihon settings Color filter and Reading mode tabs respond to real touches")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        sys.exit(1)
