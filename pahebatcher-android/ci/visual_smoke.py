#!/usr/bin/env python3
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path("visual")
ROOT.mkdir(exist_ok=True)

def run(*args: str, check: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(args, check=check, text=True, capture_output=True)

def adb(*args: str, check: bool = True) -> subprocess.CompletedProcess:
    return run("adb", *args, check=check)

def dump_ui() -> ET.Element:
    adb("shell", "uiautomator", "dump", "/sdcard/window.xml")
    adb("pull", "/sdcard/window.xml", str(ROOT / "window.xml"))
    return ET.parse(ROOT / "window.xml").getroot()

def visible_texts(root: ET.Element) -> list[str]:
    return [n.attrib.get("text", "") for n in root.iter("node") if n.attrib.get("text")]

def assert_text(text: str) -> None:
    root = dump_ui()
    values = visible_texts(root)
    if text not in values:
        raise AssertionError(f"Missing UI text {text!r}. Visible text: {values}")

def tap_text(text: str) -> None:
    root = dump_ui()
    for node in root.iter("node"):
        if node.attrib.get("text") != text:
            continue
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if not match:
            continue
        x1, y1, x2, y2 = map(int, match.groups())
        adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        time.sleep(2)
        return
    raise AssertionError(f"Could not find tappable text {text!r}")

def screenshot(name: str) -> None:
    with (ROOT / name).open("wb") as fp:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], check=True, stdout=fp)

assert_text("PaheBatcher")
assert_text("Explore")
assert_text("Downloads")
assert_text("Settings")
screenshot("explore.png")

tap_text("Settings")
assert_text("Web verification")
assert_text("Open verification browser")
screenshot("settings.png")

tap_text("Open verification browser")
assert_text("Step 1 of 2 · AnimePahe")
assert_text("I’ve completed AnimePahe")
screenshot("verification.png")

pid = adb("shell", "pidof", "com.night.pahebatcher").stdout.strip()
if not pid:
    raise SystemExit("PaheBatcher process is not running after visual smoke")
print(f"PaheBatcher visual smoke passed with pid {pid}")
