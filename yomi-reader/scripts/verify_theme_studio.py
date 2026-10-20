#!/usr/bin/env python3
"""Exercise Yomi's real Theme Studio on the Android 8 emulator.

Open the gallery through the Home UI, choose a palette, enter a custom HEX
color, restart the app and confirm preferences survived process death.
"""
import pathlib
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

REMOTE = "/sdcard/yomi-theme-hierarchy.xml"
PACKAGE = "app.yomi.reader.dev"
ACTIVITY = f"{PACKAGE}/app.yomi.reader.MainActivity"
BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def adb(*args, timeout=50):
    return subprocess.run(["adb", *args], check=True, capture_output=True,
                          text=True, timeout=timeout)


def nodes():
    for _ in range(5):
        try:
            adb("shell", "rm", "-f", REMOTE)
            adb("shell", "uiautomator", "dump", "--compressed", REMOTE)
            document = adb("exec-out", "cat", REMOTE).stdout
            return list(ET.fromstring(document).iter("node"))
        except (ET.ParseError, OSError, subprocess.SubprocessError):
            time.sleep(2)
    raise AssertionError("Cannot read live Theme Studio accessibility tree")


def find_label(label, scroll=False):
    for attempt in range(6 if scroll else 3):
        for node in nodes():
            if node.attrib.get("text") != label and node.attrib.get("content-desc") != label:
                continue
            match = BOUNDS.fullmatch(node.attrib.get("bounds", ""))
            if match:
                left, top, right, bottom = map(int, match.groups())
                if right > left and bottom > top:
                    return (left + right) // 2, (top + bottom) // 2
        if scroll:
            adb("shell", "input", "swipe", "510", "1440", "510", "620", "280")
        else:
            time.sleep(2)
    raise AssertionError(f"No visible Theme Studio control: {label}")


def tap(label, scroll=False):
    x, y = find_label(label, scroll=scroll)
    adb("shell", "input", "tap", str(x), str(y))


def wait_log(needle, attempts=20):
    for _ in range(attempts):
        log = adb("logcat", "-d", "-v", "brief", "YomiStartup:I", "*:S").stdout
        if needle in log:
            return
        time.sleep(2)
    raise AssertionError(f"Missing actual app checkpoint: {needle}")


def capture(path):
    pathlib.Path(path).parent.mkdir(parents=True, exist_ok=True)
    with open(path, "wb") as dest:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], check=True,
                       stdout=dest, timeout=60)
    assert pathlib.Path(path).stat().st_size > 4096, "Empty Theme Studio screenshot"


def restart():
    adb("shell", "am", "force-stop", PACKAGE)
    adb("logcat", "-c")
    adb("shell", "am", "start", "-n", ACTIVITY)


def main(folder):
    find_label("Theme studio")
    tap("Theme studio")
    find_label("Choose a palette")
    tap("Paper theme")
    wait_log("appearance-selected palette=paper")
    capture(f"{folder}/theme-paper-gallery.png")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    restart()
    wait_log("appearance-restored palette=paper")

    tap("Theme studio")
    find_label("Choose a palette")
    tap("Custom theme color in hexadecimal", scroll=True)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    for _ in range(6):
        adb("shell", "input", "keyevent", "KEYCODE_DEL")
    adb("shell", "input", "text", "52A6CF")
    # Android's back key dismisses the IME, keeping the sheet visible.
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    tap("Use color", scroll=True)
    wait_log("appearance-selected palette=custom hex=52A6CF")
    capture(f"{folder}/theme-custom-gallery.png")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    restart()
    wait_log("appearance-restored palette=custom hex=52A6CF")

    tap("Theme studio")
    find_label("Choose a palette")
    tap("Midnight theme")
    wait_log("appearance-selected palette=midnight")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    print("PASS: gallery swatches, custom HEX, restart persistence and default restore")


if __name__ == "__main__":
    try:
        main(sys.argv[1] if len(sys.argv) > 1 else ".")
    except Exception as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        sys.exit(1)
