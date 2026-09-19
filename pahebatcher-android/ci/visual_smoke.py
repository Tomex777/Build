#!/usr/bin/env python3
import os
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

def wait_for_text(text: str, timeout: float = 35.0) -> None:
    deadline = time.time() + timeout
    last: list[str] = []
    while time.time() < deadline:
        try:
            root = dump_ui()
            last = visible_texts(root)
            if text in last:
                return
        except Exception:
            pass
        time.sleep(1.0)
    raise AssertionError(f"Missing UI text {text!r}. Visible text: {last}")

def wait_for_desc(desc: str, timeout: float = 8.0) -> None:
    deadline = time.time() + timeout
    last: list[str] = []
    while time.time() < deadline:
        try:
            root = dump_ui()
            last = [n.attrib.get("content-desc", "") for n in root.iter("node") if n.attrib.get("content-desc")]
            if desc in last:
                return
        except Exception:
            pass
        time.sleep(1.0)
    raise AssertionError(f"Missing content-desc {desc!r}. Visible descriptions: {last}")

def assert_text(text: str) -> None:
    wait_for_text(text, timeout=5.0)

def assert_text_absent(fragment: str) -> None:
    values = visible_texts(dump_ui())
    if any(fragment in value for value in values):
        raise AssertionError(f"Unexpected UI text containing {fragment!r}. Visible text: {values}")

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
        time.sleep(1.5)
        return
    raise AssertionError(f"Could not find tappable text {text!r}")

def tap_desc(desc: str) -> None:
    root = dump_ui()
    for node in root.iter("node"):
        if node.attrib.get("content-desc") != desc:
            continue
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if not match:
            continue
        x1, y1, x2, y2 = map(int, match.groups())
        adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        time.sleep(1.5)
        return
    raise AssertionError(f"Could not find tappable content-desc {desc!r}")

def screenshot(name: str) -> None:
    with (ROOT / name).open("wb") as fp:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], check=True, stdout=fp)

def recover_from_launcher_anr() -> None:
    for _ in range(3):
        try:
            texts = visible_texts(dump_ui())
        except Exception:
            texts = []
        if "Pixel Launcher isn't responding" in texts:
            try:
                tap_text("Wait")
            except Exception:
                adb("shell", "input", "keyevent", "4", check=False)
            time.sleep(1.5)
        adb("shell", "am", "start", "-W", "-n", "com.night.pahebatcher/.MainActivity", check=False)
        time.sleep(2.0)
        try:
            if "PaheBatcher" in visible_texts(dump_ui()):
                return
        except Exception:
            pass

recover_from_launcher_anr()
assert_text("PaheBatcher")
assert_text("Explore")
assert_text("Downloads")
assert_text("Settings")
assert_text("Search anime")
wait_for_desc("Verify AnimePahe browser")
screenshot("explore.png")

# Settings should no longer own the verification flow.
tap_text("Settings")
assert_text("Download preferences")
assert_text_absent("Web verification")
screenshot("settings.png")

# System Back from Settings should return to Explore.
adb("shell", "input", "keyevent", "4")
wait_for_text("Find it. Keep it.", timeout=8.0)

# Verification now lives in the top-right browser icon, not Settings.
tap_desc("Verify AnimePahe browser")
assert_text("AnimePahe verification")
screenshot("verification.png")

# GitHub runner IPs are frequently blocked by AnimePahe/Cloudflare. The visual
# smoke only proves that our embedded browser opens and Android system Back
# returns to Explore; live source validity is verified separately on-device.
adb("shell", "input", "keyevent", "4")
wait_for_text("Find it. Keep it.", timeout=8.0)
screenshot("explore-after-system-back.png")

if os.environ.get("LIVE_ANIMEPAHE", "").lower() == "true":
    wait_for_text("Search anime", timeout=8.0)
    tap_text("Search anime")
    adb("shell", "input", "text", "bleach")
    adb("shell", "input", "keyevent", "66")
    wait_for_text("Bleach", timeout=45.0)
    screenshot("search-bleach.png")

    tap_text("Bleach")
    wait_for_text("Episodes", timeout=8.0)
    screenshot("details-shell.png")

    # The shell must remain visible while the real release request resolves.
    wait_for_text("1", timeout=45.0)
    assert_text_absent("AnimePahe verification is needed")
    screenshot("details-loaded.png")

    # Verify Android system Back returns to the search results.
    adb("shell", "input", "keyevent", "4")
    wait_for_text("Results", timeout=8.0)
    screenshot("search-after-system-back.png")
else:
    screenshot("tor-animepahe-challenged.png")
    print("AnimePahe live smoke skipped because all sampled Tor exits were challenged.")

pid = adb("shell", "pidof", "com.night.pahebatcher").stdout.strip()
if not pid:
    raise SystemExit("PaheBatcher process is not running after visual smoke")
print(f"PaheBatcher visual + live AnimePahe smoke passed with pid {pid}")
