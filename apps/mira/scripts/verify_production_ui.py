#!/usr/bin/env python3
"""Exercise the installed production APK through public UI, without test hooks."""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

evidence = Path(sys.argv[1])
apk = sys.argv[2]
package = "app.mira.android"


def adb(*args):
    return subprocess.check_output(["adb", *args], text=True)


def tree():
    adb("shell", "uiautomator", "dump", "/sdcard/mira-production.xml")
    return ET.fromstring(adb("shell", "cat", "/sdcard/mira-production.xml"))


def find(attribute, value, timeout=30):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        root = tree()
        for node in root.iter("node"):
            if node.get(attribute) == value:
                return node
        time.sleep(1)
    raise AssertionError(f"Missing visible production UI: {attribute}={value}")


def tap(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.attrib["bounds"]))
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def text(value):
    tap(find("text", value))


def capture(name):
    root = tree()
    ET.ElementTree(root).write(evidence / f"{name}.xml")
    with (evidence / f"{name}.png").open("wb") as output:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=output, check=True)


def settings():
    text("More")
    text("Settings")
    find("text", "Incognito")
    return find("checkable", "true")


text("Browse")
find("text", "Search movies and TV")
capture("browse")
text("More")
text("Sources & extensions")
find("text", "Installed sources")
capture("sources")
tap(find("content-desc", "Back"))
text("Downloads")
find("text", "No downloads")
capture("downloads")
tap(find("content-desc", "Back"))
text("Settings")
find("text", "Incognito")
switch = find("checkable", "true")
original = switch.get("checked")
tap(switch)
expected = "false" if original == "true" else "true"
assert find("checkable", "true").get("checked") == expected
capture("settings-changed")
adb("shell", "input", "keyevent", "KEYCODE_HOME")
adb("shell", "am", "start", "-W", "-n", package + "/.MainActivity")
assert find("checkable", "true").get("checked") == expected
capture("foreground")
adb("shell", "am", "force-stop", package)
adb("shell", "am", "start", "-W", "-n", package + "/.MainActivity")
assert settings().get("checked") == expected
capture("process-recreated")
adb("shell", "am", "force-stop", package)
assert "Success" in adb("install", "--no-streaming", "-r", apk)
adb("shell", "am", "start", "-W", "-n", package + "/.MainActivity")
assert settings().get("checked") == expected
capture("reinstall-preserved")
tap(find("checkable", "true"))
assert find("checkable", "true").get("checked") == original
tap(find("content-desc", "Back"))
text("About Mira")
find("text", "Your movie and TV library")
capture("about")
if "--real-playback" in sys.argv:
    # Use the real production source and player, with no instrumentation injection.
    adb("shell", "am", "force-stop", package)
    adb("shell", "am", "start", "-W", "-n", package + "/.MainActivity")
    text("Browse")
    tap(find("class", "android.widget.EditText"))
    adb("shell", "input", "text", "Night%sof%sthe%sLiving%sDead")
    tap(find("content-desc", "Search"))
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    deadline = time.monotonic() + 120
    movie = None
    while time.monotonic() < deadline and movie is None:
        for node in tree().iter("node"):
            label = node.get("text", "")
            if node.get("class") != "android.widget.EditText" and "night" in label.lower() and "living dead" in label.lower():
                movie = node
                break
        if movie is None:
            time.sleep(2)
    assert movie is not None, "Real Archive search did not return the expected public-domain movie"
    title = movie.get("text")
    capture("real-source-search")
    tap(movie)
    text("Add to library")
    find("text", "In library")
    capture("movie-in-library")
    # Details can contain a long synopsis above the stream buttons.
    play = None
    for _ in range(15):
        for node in tree().iter("node"):
            if node.get("clickable") == "true" and any(
                child.get("text") in ("Play", "Resume") for child in node.iter("node")
            ):
                play = node
                break
        if play is not None:
            break
        adb("shell", "input", "swipe", "160", "500", "160", "180", "400")
        time.sleep(2)
    assert play is not None, "No resolved movie playback button"
    tap(play)
    find("content-desc", "Pause", timeout=120)
    def playback_times():
        return [node.get("text") for node in tree().iter("node")
                if re.fullmatch(r"\d+:\d{2}", node.get("text", ""))]
    before = playback_times()
    time.sleep(8)
    after = playback_times()
    def seconds(value):
        minutes, remainder = map(int, value.split(":"))
        return minutes * 60 + remainder
    assert len(before) >= 2 and len(after) >= 2, "Missing production playback time labels"
    assert seconds(after[0]) > seconds(before[0]), "Production playback position did not advance"
    assert seconds(after[-1]) > 0, "Production stream duration is unknown"
    capture("real-source-playing")
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    adb("shell", "am", "start", "-W", "-n", package + "/.MainActivity")
    find("content-desc", "Pause", timeout=60)
    capture("player-foreground")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    adb("shell", "am", "force-stop", package)
    adb("shell", "am", "start", "-W", "-n", package + "/.MainActivity")
    find("text", title)
    capture("library-after-restart")
    (evidence / "real-playback-result.txt").write_text(
        "PASS: live Archive search, resolve, saved library item, advancing libVLC playback, "
        "player background/foreground, and library persistence after process restart.\n"
    )
(evidence / "production-ui-result.txt").write_text(
    "PASS: navigation, settings persistence, background/foreground, process restart, "
    "same-certificate reinstall preserving data, and application identity.\n"
    "This check does not certify real-source playback; separate playback evidence is required.\n"
)
