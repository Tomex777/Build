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
        for node in self.nodes():
            value = node.get("text", "").strip()
            if value.isdecimal():
                return int(value)
        raise AssertionError("Could not find numeric tab count in tab overview")

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
        self.tap("Search or enter address")
        self.shell("input", "text", "example.com")
        self.tap("Go")
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
        self.wait_for("My browsing")
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
            time.sleep(2)
            self.wait_for("New tab")
            result = self.shot("07-landscape-tabs")
            self.shell("settings", "put", "system", "user_rotation", "0")
            time.sleep(2)
            self.wait_for("New tab")
            return result
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
        self.tap("More")
        try:
            options = [self.label(n).lower() for n in self.nodes()]
            if not any("private" in s or "incognito" in s for s in options):
                raise AssertionError("Private browsing entry point absent; cannot assert cookie isolation")
            return "Private entry present; cookie isolation still needs separate on-device test"
        finally:
            self.shell("input", "keyevent", "4", check=False)

    def offline(self):
        # Disables both services temporarily; always restore connectivity.
        self.shell("svc", "wifi", "disable", check=False)
        self.shell("svc", "data", "disable", check=False)
        try:
            self.tap("Home")
            self.wait_for("Quick access")
            self.shot("09-offline-home")
            return "Home remains accessible with device network services toggled off"
        finally:
            self.shell("svc", "wifi", "enable", check=False)
            self.shell("svc", "data", "enable", check=False)

    def diagnostics(self):
        raw = self.adb("logcat", "-d", "-t", "2000", check=False)
        # Do not persist unfiltered logs that could include browsing content or tokens.
        lines = [ln[:450] for ln in raw.splitlines()
                 if ("FATAL EXCEPTION" in ln or
                     ("AndroidRuntime" in ln and ("FATAL" in ln or "com.night.browser" in ln)))]
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
