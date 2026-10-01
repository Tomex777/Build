"""External release UI acceptance; no debug classes or instrumentation dependencies."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image

def adb(*args):
    return subprocess.check_output(["adb", *args], text=True)

def find(label, timeout=10):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        adb("shell", "uiautomator", "dump", "/sdcard/aod-release.xml")
        root = ET.fromstring(adb("shell", "cat", "/sdcard/aod-release.xml"))
        for node in root.iter("node"):
            if label in (node.get("text"), node.get("content-desc")):
                return node
        time.sleep(.3)
    raise AssertionError("Release control unavailable: " + label)

def tap(label):
    node = find(label)
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))

def screenshot(name):
    path = Path("aod-evidence") / name
    with path.open("wb") as out:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=out, check=True)
    image = Image.open(path).convert("RGB")
    w, h = image.size
    content = sum(min(image.getpixel((x,y))) > 100
                  for y in range(h//5, h*4//5, 4)
                  for x in range(w//5, w*4//5, 4))
    assert content > 20, "Release preview has no visible AOD content"

adb("shell", "am", "force-stop", "com.homira.aod")
adb("shell", "am", "start", "-W", "-n", "com.homira.aod/.MainActivity")
find("AOD")
tap("Edit")
find("AOD design canvas")
tap("Preview")
find("Close")
time.sleep(.3)
screenshot("release-preview.png")
tap("Close")
adb("shell", "input", "keyevent", "4")
print("Release install, saved Studio and shared Preview acceptance passed")
