#!/usr/bin/env python3
"""Real Android UI automation for tracked-source EchoSpace production variant."""
from pathlib import Path
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

OUT=Path("echospace-production-qa")
OUT.mkdir(exist_ok=True)
def adb(*parts, timeout=55, capture=True):
    return subprocess.run(["adb",*parts],check=True,timeout=timeout,
                          capture_output=capture,text=capture)
def hierarchy():
    for attempt in range(4):
        try:
            adb("shell","uiautomator","dump","/sdcard/echo-ui.xml",timeout=35)
            raw=adb("exec-out","cat","/sdcard/echo-ui.xml",timeout=20).stdout
            root=ET.fromstring(raw)
            bad=[n.attrib.get("text","") for n in root.iter("node")
                 if "not responding" in n.attrib.get("text","").lower()]
            if bad:raise RuntimeError(f"Android System UI ANR dialog: {bad}")
            return list(root.iter("node"))
        except (subprocess.TimeoutExpired,ET.ParseError,subprocess.CalledProcessError):
            time.sleep(3)
    raise RuntimeError("Could not read a real Android UI tree")
def match(nodes,text,attribute):
    return next((n for n in nodes if n.attrib.get(attribute,"")==text),None)
def ensure(text,attribute="text"):
    nodes=hierarchy()
    found=match(nodes,text,attribute)
    if found is None:
        snapshot=[(n.attrib.get("text",""),n.attrib.get("content-desc","")) for n in nodes]
        raise RuntimeError(f"Missing {attribute}={text!r}; actual tree: {snapshot[:45]}")
    return found
def tap(value,attribute="content-desc"):
    node=ensure(value,attribute)
    b=node.attrib.get("bounds","")
    groups=list(map(int,re.findall(r"\d+",b)))
    if len(groups)!=4:raise RuntimeError(f"Cannot tap {value}: {b}")
    x=(groups[0]+groups[2])//2
    y=(groups[1]+groups[3])//2
    adb("shell","input","tap",str(x),str(y))
    time.sleep(2)
def capture(name):
    hierarchy()  # Verify the actual application tree, never a system crash overlay.
    with (OUT/name).open("wb") as image:
        subprocess.run(["adb","exec-out","screencap","-p"],
                       stdout=image,check=True,timeout=28)
    size=(OUT/name).stat().st_size
    if size<15_000:raise RuntimeError(f"Bad screenshot {name}: only {size} bytes")
    print("CAPTURED",name,size,flush=True)

if __name__=="__main__":
    apk=os.environ["ECHOSPACE_DEBUG_APK"]
    adb("wait-for-device",timeout=120)
    adb("install","-r",apk,timeout=180)
    adb("shell","settings","put","secure","immersive_mode_confirmations","confirmed")
    adb("shell","am","start","-W","-n","com.nexa.echospace/.MainActivity",timeout=60)
    time.sleep(9)
    ensure("EchoSpace")
    capture("01-home-fullscreen.png")
    tap("Play")
    ensure("Listening")
    capture("02-listening.png")
    tap("Open full sound mixer")
    ensure("Sound mixer")
    nodes=hierarchy()
    if any(n.attrib.get("class")=="android.widget.SeekBar" for n in nodes):
        raise RuntimeError("Default Android SeekBar violates ABR")
    capture("03-native-mixer.png")
    tap("Close mixer")
    # Live sound controls and persistent scene choice.
    try:
        tap("Deep Forest","text")
    except RuntimeError:
        # Some screen heights show the second carousel card only after a short swipe.
        adb("shell","input","swipe","870","1350","340","1350","450")
        time.sleep(2)
        tap("Deep Forest","text")
    ensure("Deep Forest")
    time.sleep(2)
    capture("04-forest.png")
    adb("shell","am","force-stop","com.nexa.echospace")
    adb("shell","am","start","-W","-n","com.nexa.echospace/.MainActivity",timeout=60)
    time.sleep(6)
    ensure("Deep Forest")
    capture("05-saved-scene-restored.png")
    log=adb("shell","logcat","-d","-s","AndroidRuntime:E").stdout
    (OUT/"android-errors.txt").write_text(log)
    if "FATAL EXCEPTION" in log:raise RuntimeError("Android fatal crash detected")
    print("ECHOSPACE_PRODUCTION_REAL_EMULATOR_UI_PASS",flush=True)
