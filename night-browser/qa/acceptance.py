#!/usr/bin/env python3
"""NIGHT Browser black-box Android QA, driven by actual ADB touches and UI dumps.

Runs against a disposable emulator/profile, not the developer's real browser data.
Only stdlib is required. Screenshots show test-only public webpages.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
from datetime import datetime, timezone
from xml.etree import ElementTree

PACKAGE = "com.night.browser"
ACTIVITY = f"{PACKAGE}/.MainActivity"


class Acceptance:
    def __init__(self, output, mode):
        self.output = Path(output)
        self.output.mkdir(parents=True, exist_ok=True)
        self.mode = mode
        self.results = []
        self.started = datetime.now(timezone.utc).isoformat()
        self.profile = self.shell("getprop", "ro.build.version.sdk", check=False).strip()
        self.device = self.shell("getprop", "ro.product.model", check=False).strip()
        self.last_xml = ""

    @staticmethod
    def adb(*args, check=True, timeout=65, binary=False):
        result = subprocess.run(["adb", *args], capture_output=True, timeout=timeout)
        if check and result.returncode:
            err = result.stderr.decode(errors="replace")
            raise RuntimeError(f"adb {' '.join(args[:4])} failed ({result.returncode}): {err[:400]}")
        return result.stdout if binary else result.stdout.decode(errors="replace")

    def shell(self, *args, check=True):
        return self.adb("shell", *args, check=check)

    def xml(self):
        last = None
        for attempt in range(3):
            try:
                self.shell("uiautomator", "dump", "/sdcard/night-qa.xml")
                data = self.shell("cat", "/sdcard/night-qa.xml")
                root = ElementTree.fromstring(data[data.index("<?xml"):])
                self.last_xml = data
                return root
            except (RuntimeError, ValueError, ElementTree.ParseError) as exc:
                last = exc
                time.sleep(1.2)
        raise RuntimeError(f"Android accessibility dump unavailable: {last}")

    def nodes(self):
        return list(self.xml().iter("node"))

    @staticmethod
    def label(node):
        return (node.get("content-desc", "") or node.get("text", "")).strip()

    def seek(self, label, partial=False):
        for node in self.nodes():
            candidate = self.label(node)
            if (label.casefold() in candidate.casefold() if partial else candidate == label):
                return node
        return None

    def wait_for(self, label, timeout=12, partial=False):
        end = time.monotonic() + timeout
        while time.monotonic() < end:
            node = self.seek(label, partial)
            if node is not None:
                return node
            time.sleep(1)
        raise AssertionError(f"Expected visible Android UI label: {label!r}")

    def tap(self, label, timeout=12, partial=False):
        node = self.wait_for(label, timeout, partial)
        coords = re.findall(r"\d+", node.get("bounds", ""))
        if len(coords) != 4:
            raise AssertionError(f"No screen bounds for {label!r}")
        x1, y1, x2, y2 = map(int, coords)
        if x2 <= x1 or y2 <= y1:
            raise AssertionError(f"Invalid tap rectangle for {label!r}")
        self.shell("input", "tap", str((x1+x2)//2), str((y1+y2)//2))
        time.sleep(.5)

    def replace_omnibar(self, text):
        # BasicTextField contains the actual current URL (e.g. about:blank).
        # A placeholder lookup fails once Gecko emits about:blank on startup.
        # Ignore input controls owned by the emulator keyboard or onboarding.
        fields = [node for node in self.nodes()
                  if node.get("package") == PACKAGE and
                  (node.get("class") == "android.widget.EditText"
                   or node.get("editable") == "true")]
        if not fields:
            raise AssertionError("No editable address input exposed to Android accessibility")
        def center_y(node):
            bounds = list(map(int, re.findall(r"\d+", node.get("bounds", ""))))
            return (bounds[1] + bounds[3]) / 2 if len(bounds) == 4 else -1
        field = max(fields, key=center_y)
        bounds = list(map(int, re.findall(r"\d+", field.get("bounds", ""))))
        if len(bounds) != 4:
            raise AssertionError("Address field exists but has no bounds")
        self.shell("input", "tap", str((bounds[0] + bounds[2])//2),
                   str((bounds[1] + bounds[3])//2))
        time.sleep(.7)
        # Move cursor to end and clear known visible characters in a single
        # adb invocation; do not assume focus selects all existing URL text.
        current = field.get("text", "")
        self.shell("input", "keyevent", "123")
        if current:
            self.shell("input", "keyevent", *(["67"] * (min(len(current), 400) + 2)))
        self.shell("input", "text", text)
        time.sleep(.3)
        deadline = time.monotonic() + 3
        while time.monotonic() < deadline:
            candidate = [node.get("text", "") for node in self.nodes()
                         if node.get("package") == PACKAGE and
                         (node.get("class") == "android.widget.EditText"
                          or node.get("editable") == "true")]
            if any(text in value for value in candidate):
                return
            time.sleep(.4)
        raise AssertionError(f"ADB text entry did not reach NIGHT's address field: {candidate!r}")

    def shot(self, name):
        path = self.output / (name + ".png")
        raw = self.adb("exec-out", "screencap", "-p", binary=True)
        if not raw.startswith(b"\x89PNG"):
            raise RuntimeError("Device screenshot was not a PNG")
        path.write_bytes(raw)
        return path.name

    def count_tabs(self):
        """Compose shows a heading 'Tabs' and a separate numeric tab count."""
        self.wait_for("New tab")
        labels = [node.get("text", "") or node.get("content-desc", "")
                  for node in self.nodes() if node.get("package") == PACKAGE]
        for value in labels:
            match = re.search(r"\bRegular\s+(\d+)\b", value)
            if match:
                return int(match.group(1))
        raise AssertionError("Regular tab count not accessible: " + repr(labels[:16]))

    def record(self, case, kind, func):
        print(f"[QA] {kind}: {case}", flush=True)
        t0 = time.monotonic()
        try:
            detail = func() or ""
            status = "PASS"
        except Exception as exc:  # Each independent case records evidence, not fabricated passes.
            detail = f"{type(exc).__name__}: {exc}"
            status = "FAIL"
            try:
                self.shot("failure-" + case.lower().replace(" ", "-"))
            except Exception:
                pass
        self.results.append({"case": case, "category": kind, "status": status,
                             "seconds": round(time.monotonic()-t0, 1), "detail": str(detail)})
        print(f"[QA] {status}: {case} — {detail}", flush=True)
        return status == "PASS"

    def gap(self, name, detail):
        self.results.append({"case": name, "category": "contract",
                             "status": "NOT_RUN", "seconds": 0, "detail": detail})

    def start(self):
        self.shell("am", "force-stop", PACKAGE, check=False)
        self.shell("am", "start", "-W", "-n", ACTIVITY)
        self.wait_for("Home", timeout=30)
        self.wait_for("More")
        self.wait_for("Tabs")
        return "Launched app and found bottom navigation"

    def home(self):
        self.wait_for("Quick access")
        return self.shot("01-home")

    def navigate(self):
        self.replace_omnibar("example.com")
        if self.seek("Go to address or search") is not None:
            self.tap("Go to address or search")
        elif self.seek("Go") is not None:
            self.tap("Go")
        else:
            self.shell("input", "keyevent", "66")
        end = time.monotonic() + 30
        loaded = False
        while time.monotonic() < end:
            # Verify the address changes to a fully resolved URL. A bare typed query
            # does not prove navigation. Gecko's webpage text may be inaccessible.
            for node in self.nodes():
                text = node.get("text", "")
                if text.startswith("https://example.com"):
                    loaded = True
                    break
            if loaded:
                break
            time.sleep(1)
        if not loaded:
            raise AssertionError("Navigation did not expose a resolved https://example.com URL")
        time.sleep(3)
        return self.shot("02-real-website")

    def bookmark_library(self):
        self.tap("More")
        self.tap("Bookmark page")
        time.sleep(.5)
        self.tap("More")
        self.tap("Library")
        self.wait_for("example.com", timeout=8, partial=True)
        return self.shot("03-library-bookmark")

    def journey(self):
        self.tap("More")
        self.tap("Journeys")
        self.wait_for("Journeys")
        self.wait_for("example.com", timeout=8, partial=True)
        return self.shot("04-journey-history")

    def snapshot_screen(self):
        self.tap("More")
        self.tap("Time Machine")
        return self.shot("05-time-machine")

    def ten_tabs(self):
        self.tap("Tabs")
        initial = self.count_tabs()
        if initial != 1:
            raise AssertionError(f"Expected one tab on clean profile, found {initial}")
        # Actual UI gestures, rather than opening a hidden controller API.
        for expected in range(2, 11):
            self.tap("New tab")
            self.tap("Tabs")
            count = self.count_tabs()
            if count != expected:
                raise AssertionError(f"After creating tab {expected}, UI reports {count}")
        shot = self.shot("06-ten-tabs")
        # Visual 2-column and real thumbnail quality require human screenshot review.
        return f"{shot}; count=10 (thumbnail quality requires visual review)"

    def rotate(self):
        self.shell("settings", "put", "system", "accelerometer_rotation", "0")
        try:
            self.shell("settings", "put", "system", "user_rotation", "1")
            time.sleep(3)
            path = self.shot("07-landscape-tabs")
            import struct
            image_bytes = (self.output / path).read_bytes()
            width, height = struct.unpack(">II", image_bytes[16:24])
            if width <= height:
                raise AssertionError(f"Android did not produce landscape screenshot: {width}x{height}")
            try:
                self.wait_for("New tab", timeout=7)
            except AssertionError as exc:
                pid = self.shell("pidof", PACKAGE, check=False).strip()
                raise AssertionError(f"App no longer displays tabs after rotation (pid={pid or 'none'}): {exc}")
            self.shell("settings", "put", "system", "user_rotation", "0")
            time.sleep(2)
            self.wait_for("New tab")
            return f"{path}; screenshot={width}x{height}"
        finally:
            self.shell("settings", "put", "system", "user_rotation", "0", check=False)
            self.shell("settings", "put", "system", "accelerometer_rotation", "1", check=False)

    def restore(self):
        self.shell("am", "force-stop", PACKAGE)
        self.shell("am", "start", "-W", "-n", ACTIVITY)
        self.tap("Tabs", timeout=35)
        restored = self.count_tabs()
        self.shot("08-after-process-restart")
        if restored < 10:
            raise AssertionError(f"Session restore lost tabs: 10 before restart, {restored} after")
        return f"Restored {restored} tabs after force-stop"

    def private_tabs(self):
        # The real private entry is Tabs > Private > New private tab.
        # The normal-mode More menu does not expose a private entry.
        self.tap("Tabs")
        labels = [self.label(n) for n in self.nodes()]
        candidate = next((v for v in labels if re.search(r"\bPrivate\s+\d+\b", v)), None)
        if candidate is None:
            raise AssertionError("Private tabs filter not accessible")
        self.tap(candidate)
        self.tap("New private tab")
        self.wait_for("Private browsing", timeout=12)
        return "Opened actual Gecko private tab (cookie isolation remains unverified)"

    def offline(self):
        # The previous case deliberately leaves a PRIVATE tab active. Its Home
        # should display "Private browsing", not the regular Quick access tiles.
        # Verify that private Home works offline, then explicitly switch to a
        # regular tab and verify the regular start page; never confuse profiles.
        self.shell("svc", "wifi", "disable", check=False)
        self.shell("svc", "data", "disable", check=False)
        try:
            self.tap("Home")
            self.wait_for("Private browsing")
            self.shot("09-offline-private-home")
            self.tap("Tabs")
            labels = [self.label(n) for n in self.nodes()
                      if n.get("package") == PACKAGE]
            regular = next((value for value in labels
                            if re.fullmatch(r"Regular\\s+\\d+", value)), None)
            if regular is None:
                raise AssertionError("Cannot select regular profile from private tabs")
            self.tap(regular)
            self.tap("New tab")
            self.wait_for("Quick access")
            self.shot("09-offline-regular-home")
            return "Private and regular Home accessible offline with correct profile isolation"
        finally:
            self.shell("svc", "wifi", "enable", check=False)
            self.shell("svc", "data", "enable", check=False)

    def diagnostics(self):
        raw = self.adb("logcat", "-d", "-t", "4000", check=False)
        # Attribute exceptions to this app's PROCESS, not unrelated emulator
        # system processes. Keep only exception descriptors and our own frames;
        # never persist arbitrary logcat URLs, cookies or account data.
        all_lines = raw.splitlines()
        context = []
        for i, ln in enumerate(all_lines):
            if "AndroidRuntime" in ln and "Process: com.night.browser" in ln:
                context.extend(all_lines[max(0, i - 1):i + 14])
        lines = list(dict.fromkeys(ln[:450] for ln in context
                 if "AndroidRuntime" in ln and ("FATAL EXCEPTION" in ln
                    or "Process: com.night.browser" in ln
                    or "Exception:" in ln or "Error:" in ln
                    or "at com.night.browser." in ln)))
        (self.output / "crashes.txt").write_text("\n".join(lines) + "\n")
        if lines:
            self.results.append({"case": "Crash diagnostics", "category": "smoke",
                                 "status": "FAIL", "seconds": 0,
                                 "detail": f"Filtered AndroidRuntime lines: {len(lines)}"})
        else:
            self.results.append({"case": "Crash diagnostics", "category": "smoke",
                                 "status": "PASS", "seconds": 0,
                                 "detail": "No matched fatal AndroidRuntime lines in recent logcat"})

    def finish(self):
        self.diagnostics()
        report = {"started_utc": self.started, "finished_utc": datetime.now(timezone.utc).isoformat(),
                  "android_api": self.profile, "device_model": self.device,
                  "mode": self.mode, "disposable_profile": True, "results": self.results}
        (self.output / "report.json").write_text(json.dumps(report, indent=2) + "\n")
        lines = ["# NIGHT Browser — ADB acceptance evidence", "",
                 f"Android API: {self.profile}; model: {self.device}; mode: {self.mode}.",
                 "Tests operated a fresh disposable emulator. No physical phone test is implied.",
                 "Screenshots are real emulator captures; no claim of manual visual review.", "",
                 "| Test | Gate | Status | Detail |", "|---|---|---|---|"]
        for r in self.results:
            msg = r["detail"].replace("|", "/").replace("\n", " ")[:230]
            lines.append(f'| {r["case"]} | {r["category"]} | {r["status"]} | {msg} |')
        lines += ["", "## Outstanding hands-on acceptance",
                  "Check rendered page content, real thumbnail pixels, file chooser/uploads, actual"
                  " downloaded bytes, PDF save/open, Peek long-press, per-site permissions,"
                  " sign-in (test accounts only), incognito cookie separation, Site Studio origin"
                  " boundaries, AI explicit consent, memory/battery, slow network and physical"
                  " devices. Missing features are not considered passing tests.", ""]
        (self.output / "report.md").write_text("\n".join(lines))
        failed = [r for r in self.results if r["status"] == "FAIL" and
                  (self.mode == "full" or r["category"] == "smoke")]
        print(f"[QA] {sum(r['status']=='PASS' for r in self.results)} passed; "
              f"{sum(r['status']=='FAIL' for r in self.results)} failed; "
              f"{len(failed)} gating failures", flush=True)
        return 1 if failed else 0

    def run(self):
        self.adb("wait-for-device", timeout=120)
        self.adb("logcat", "-c", check=False)
        self.adb("shell", "pm", "clear", PACKAGE)
        if not self.record("App starts", "smoke", self.start):
            return self.finish()
        self.record("Home layout", "smoke", self.home)
        navigated = self.record("HTTPS real site navigation", "smoke", self.navigate)
        if navigated:
            self.record("Bookmark in Library", "smoke", self.bookmark_library)
            self.record("Journey visit persists in UI", "smoke", self.journey)
        else:
            self.gap("Bookmark and Journey", "Blocked by failed navigation; retest after fix")
        self.record("Time Machine entry", "smoke", self.snapshot_screen)
        tabs_ok = self.record("Ten tab touch flow", "smoke", self.ten_tabs)
        if tabs_ok:
            self.record("Landscape tabs", "smoke", self.rotate)
            self.record("Tab session restore", "contract", self.restore)
        else:
            self.gap("Session restore", "Could not create ten tabs")
        self.record("Private mode entry", "contract", self.private_tabs)
        self.record("Offline home", "smoke", self.offline)
        self.gap("External compatibility and privacy scenarios",
                 "Uploads, downloads, auth, permissions, Peek, PDF rendering, consent,"
                 " cookie isolation, slow network, battery and physical-device testing"
                 " require test fixtures or live/manual execution")
        return self.finish()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="night-browser/qa/results")
    parser.add_argument("--mode", choices=("smoke", "full"), default="smoke",
                        help="full gates known unfinished contracts, smoke gates basic flows")
    args = parser.parse_args()
    try:
        return Acceptance(args.output, args.mode).run()
    except Exception as exc:
        print(f"[QA] Harness failure: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
