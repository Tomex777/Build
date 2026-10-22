#!/usr/bin/env python3
"""NIGHT Browser Wave-2 black-box Android regression matrix (actual ADB actions).

Requires a throwaway emulator, running wave2_fixture.py on the host and installed
com.night.browser. This is not a source-inspection or screenshot-mock test.
Failure, blocking and unverified evidence remain visible in JSON; no fake passes.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
from urllib.request import urlopen
from xml.etree import ElementTree

PKG = "com.night.browser"
URL = "http://10.0.2.2:8765"
STATUS = "http://127.0.0.1:8765/__qa/status"


class Blocked(Exception):
    pass


def adb(*args, timeout=70, check=True, binary=False):
    proc = subprocess.run(["adb", *args], capture_output=True, timeout=timeout)
    if check and proc.returncode:
        raise RuntimeError("ADB command failed: " + " ".join(args[:3]) + " "
                           + proc.stderr.decode(errors="replace")[:180])
    return proc.stdout if binary else proc.stdout.decode(errors="replace")


def shell(*args, **kwargs):
    return adb("shell", *args, **kwargs)


def fixture():
    with urlopen(STATUS, timeout=5) as conn:
        return json.load(conn)


def hits(path):
    return fixture()["count"].get(path, 0)


def wait_hit(path, before, timeout=24):
    until = time.monotonic() + timeout
    while time.monotonic() < until:
        if hits(path) > before:
            return hits(path)
        time.sleep(.75)
    raise AssertionError("Fixture never received Gecko request for " + path)


class Device:
    def __init__(self, out, apk=None):
        self.out = Path(out)
        self.out.mkdir(parents=True, exist_ok=True)
        self.rows = []
        self.ready = set()
        self.apk = apk
        self.boot = datetime.now(timezone.utc).isoformat()
        self.api = shell("getprop", "ro.build.version.sdk").strip()
        self.model = shell("getprop", "ro.product.model").strip()
        self.serial = adb("get-serialno").strip()
        self.size = shell("wm", "size").strip()[:85]
        self.has_fixture = False

    def ui(self):
        # Android can emit "UI hierchary dumped" before the XML itself.
        for _ in range(3):
            shell("uiautomator", "dump", "/sdcard/night-wave2.xml", check=False)
            data = shell("cat", "/sdcard/night-wave2.xml", check=False)
            try:
                return list(ElementTree.fromstring(data[data.index("<?xml"):]).iter("node"))
            except (ValueError, ElementTree.ParseError):
                time.sleep(1)
        raise AssertionError("Android accessibility dump unavailable")

    def find(self, label, partial=False):
        for node in self.ui():
            values = [node.get("text", ""), node.get("content-desc", "")]
            if any(label.casefold() in x.casefold() if partial else x.casefold() == label.casefold()
                   for x in values if x):
                return node
        return None

    def click(self, label, partial=False, timeout=12, long=False):
        # Gboard can occlude the bottom browser chrome while UIAutomator still
        # exposes those hidden controls. Dismiss the visible IME before touching
        # Home/Tabs/More, otherwise an ADB tap lands on a keyboard key instead.
        # Only use mInputShown (not stale mShowRequested), and only for chrome.
        if label in ("Home", "Tabs", "More", "Back", "Forward"):
            ime = shell("dumpsys", "input_method", check=False)
            if re.search(r"\bmInputShown=true\b", ime):
                shell("input", "keyevent", "4")
                time.sleep(.8)
        deadline = time.monotonic() + timeout
        node = None
        while time.monotonic() < deadline:
            node = self.find(label, partial)
            if node is not None:
                break
            time.sleep(.7)
        if node is None:
            raise AssertionError("Missing accessible control: " + label)
        bounds = list(map(int, re.findall(r"\d+", node.get("bounds", ""))))
        if len(bounds) != 4 or bounds[2] <= bounds[0] or bounds[3] <= bounds[1]:
            raise AssertionError("Invalid UI touch bounds for " + label)
        x, y = (bounds[0] + bounds[2]) // 2, (bounds[1] + bounds[3]) // 2
        if long:
            shell("input", "swipe", str(x), str(y), str(x), str(y), "1100")
        else:
            shell("input", "tap", str(x), str(y))
        time.sleep(.6)

    def edit(self, raw):
        # Gboard can expose unrelated fields or an onboarding popup.
        # Interact only with NIGHT-owned editable nodes.
        fields = [n for n in self.ui() if n.get("package") == PKG and
                  (n.get("class") == "android.widget.EditText"
                   or n.get("editable") == "true")]
        if not fields:
            raise AssertionError("NIGHT omnibar is not exposed as editable")
        def bottom(node):
            b = list(map(int, re.findall(r"\d+", node.get("bounds", ""))))
            return b[3] if len(b) == 4 else -1
        field = max(fields, key=bottom)
        b = list(map(int, re.findall(r"\d+", field.get("bounds", ""))))
        shell("input", "tap", str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2))
        time.sleep(.3)
        current = field.get("text", "")
        if current:
            shell("input", "keyevent", "123", *(["67"] * min(len(current) + 4, 320)))
        shell("input", "text", raw)
        deadline = time.monotonic() + 3
        while time.monotonic() < deadline:
            if any(raw in n.get("text", "") for n in self.ui()
                   if n.get("package") == PKG and
                   (n.get("class") == "android.widget.EditText"
                    or n.get("editable") == "true")):
                return
            time.sleep(.4)
        raise AssertionError("ADB URL not reflected in NIGHT omnibar (IME focus?)")

    def submit_address(self):
        if self.find("Go to address or search") is not None:
            self.click("Go to address or search")
        elif self.find("Go") is not None:
            self.click("Go")
        else:
            # Physical keyboard IME Enter is a real submit gesture, not an API bypass.
            shell("input", "keyevent", "66")

    def go(self, path):
        before = hits(path)
        self.click("Home")
        self.edit(URL + path)
        self.submit_address()
        wait_hit(path, before)
        return "fixture received real Gecko load for " + path

    def img(self, case):
        png = adb("exec-out", "screencap", "-p", binary=True)
        if not png.startswith(b"\x89PNG"):
            raise AssertionError("Invalid PNG screenshot bytes")
        path = self.out / (case + ".png")
        path.write_bytes(png)
        return path.name

    def test(self, key, name, action, *, prerequisites=(), screenshot=False):
        missing = [x for x in prerequisites if x not in self.ready]
        begin = time.monotonic()
        if missing:
            status, detail = "BLOCKED", "Prerequisite failed: " + ",".join(missing)
        else:
            try:
                detail = str(action() or "Verified by on-device action")
                status = "PASS"
                self.ready.add(key)
            except Blocked as exc:
                status, detail = "NOT_RUN", str(exc)
            except Exception as exc:
                status, detail = "FAIL", type(exc).__name__ + ": " + str(exc)[:240]
        photo = None
        # Screenshots are only taken on our synthetic fixture pages, never
        # authenticating personal accounts or real browser profiles.
        if screenshot and status in ("PASS", "FAIL"):
            try:
                photo = self.img(key)
            except Exception as exc:
                detail += "; screenshot error: " + type(exc).__name__
        self.rows.append({"id": key, "case": name, "status": status,
                          "seconds": round(time.monotonic() - begin, 2),
                          "evidence": detail, "screenshot": photo})
        print("[WAVE2] " + key + " " + status + " " + name + " — " + detail, flush=True)

    def cold_start(self):
        shell("pm", "clear", PKG)
        shell("am", "force-stop", PKG, check=False)
        output = shell("am", "start", "-W", "-n", PKG + "/.MainActivity")
        self.click("Home", timeout=35)
        elapsed = re.search(r"TotalTime:\s*(\d+)", output)
        return "Activity displayed; ActivityManager TotalTime=" + (
            elapsed.group(1) + "ms" if elapsed else "not reported")

    def external(self):
        self.click("Home")
        self.edit("https://example.com")
        self.submit_address()
        self.click("Tabs")
        self.click("Example Domain", partial=True, timeout=28)
        return "Real external HTTPS Example Domain loaded and selected via tab overview"

    def redirect(self):
        before = hits("/landing")
        self.go("/redirect")
        wait_hit("/landing", before)
        return "HTTP 302 followed by Gecko (landing request logged)"

    def link(self, path, label):
        previous = hits(path)
        self.click(label, partial=True)
        wait_hit(path, previous)
        return "Clicked page link and fixture observed " + path

    def verify_title(self, title):
        # The fixture records a request at HTTP accept, before Gecko commits
        # HTML. Leaving WEB immediately releases GeckoView and can suspend its
        # content/title update; first demand real on-screen document content.
        deadline = time.monotonic() + 22
        while self.find(title, partial=True) is None:
            if time.monotonic() >= deadline:
                raise AssertionError("Gecko page content never committed: " + title)
            time.sleep(.6)
        self.click("Tabs")
        # TabPanel scrolls the active tab into the viewport, including when
        # >10 previously opened tabs would otherwise hide its card. Verify the
        # real tab title too; on-page heading alone cannot pass this check.
        deadline = time.monotonic() + 14
        while self.find(title, partial=True) is None:
            if time.monotonic() >= deadline:
                raise AssertionError("Committed page missing from active tab card: " + title)
            time.sleep(.6)
        self.click(title, partial=True)
        return "Gecko page and active tab card both confirmed " + title

    def regular_count(self):
        # Compose renders "Regular  10" inside a FilterChip (not a naked 10).
        labels = [n.get("text", "") or n.get("content-desc", "") for n in self.ui()
                  if n.get("package") == PKG]
        numbers = [int(match.group(1)) for label in labels
                   if (match := re.search(r"\bRegular\s+(\d+)\b", label))]
        if not numbers:
            raise AssertionError("Regular tab count not accessible; " + repr(labels[:16]))
        return max(numbers)

    def ten_tabs(self):
        self.click("Tabs")
        initial = self.regular_count()
        if initial > 10:
            raise Blocked("Already >10 tabs; isolated reset required")
        for _ in range(initial, 10):
            self.click("New tab")
            self.click("Tabs")
        if self.regular_count() != 10:
            raise AssertionError("10 regular tabs not visible in overview")
        return "Created up to ten tabs via on-device controls"

    def rotate(self):
        shell("settings", "put", "system", "accelerometer_rotation", "0")
        try:
            shell("settings", "put", "system", "user_rotation", "1")
            time.sleep(2.5)
            data = adb("exec-out", "screencap", "-p", binary=True)
            import struct
            w, h = struct.unpack(">II", data[16:24])
            if w <= h:
                raise AssertionError("landscape screen dimensions invalid")
            self.click("Tabs")
            return "Rotated screen " + str(w) + "x" + str(h)
        finally:
            shell("settings", "put", "system", "user_rotation", "0", check=False)
            shell("settings", "put", "system", "accelerometer_rotation", "1", check=False)

    def restart(self):
        shell("am", "force-stop", PKG)
        shell("am", "start", "-W", "-n", PKG + "/.MainActivity")
        self.click("Tabs")
        if self.regular_count() != 10:
            raise AssertionError("Regular tab count not restored after force-stop")
        return "After app restart ten tabs restored"

    def private(self):
        # Control: regular browsing MUST send its same-origin cookie before
        # asserting that a private profile doesn't leak it.
        self.go("/cookie/set")
        self.go("/cookie/echo")
        if not list(fixture()["cookie_seen"].values())[-1]:
            raise AssertionError("Regular cookie was not carried to echo")
        self.click("Tabs")
        labels = [n for n in self.ui() if re.fullmatch(
            r"Private\s+\d+", n.get("text", "") or n.get("content-desc", ""))]
        if not labels:
            raise Blocked("Private tab filter absent in combined build")
        name = labels[0].get("text", "") or labels[0].get("content-desc", "")
        self.click(name)
        self.click("New private tab")
        return "Private tab created, regular cookie control observed"

    def ensure_regular(self):
        """Select a real normal Gecko tab, not only the regular grid filter.

        A previous report-only API36 run claimed private-cookie PASS then kept
        downloading inside a private tab. That caused a legitimate private SAF
        prompt to poison every following download/PDF case. Never silently
        skip profile restoration or count a test of the wrong profile as PASS.
        """
        self.click("Tabs")
        regular = next((n for n in self.ui() if re.search(
            r"\bRegular\s+\d+\b", n.get("text", "") or n.get("content-desc", ""))), None)
        # Different API-level accessibility trees can append role/selection
        # descriptions to FilterChips; still touch the actual Regular control.
        if regular is None:
            regular = self.find("Regular", partial=True)
        if regular is None:
            raise AssertionError("Regular tab filter absent; cannot verify normal downloads")
        label = regular.get("text", "") or regular.get("content-desc", "")
        self.click(label)
        until = time.monotonic() + 12
        while self.find("New tab") is None:
            if time.monotonic() >= until:
                raise AssertionError("Regular filter selected but normal New tab is absent")
            time.sleep(.5)
        self.click("New tab")
        until = time.monotonic() + 12
        while self.find("Quick access") is None:
            if time.monotonic() >= until:
                raise AssertionError("Normal tab did not become active; private-mode state persists")
            time.sleep(.5)
        if self.find("New private tab") is not None:
            raise AssertionError("Private tab controls remain active on normal Home")
        return "Normal Gecko tab selected and regular Home verified"

    def cookie_isolation(self):
        try:
            self.go("/cookie/echo")
            seen = list(fixture()["cookie_seen"].values())
            if len(seen) < 2 or not seen[-2]:
                raise AssertionError("No positive regular-cookie control visit")
            if seen[-1]:
                raise AssertionError("PRIVATE COOKIE LEAK to same origin")
            return "Control cookie present in regular, absent in private"
        finally:
            # This is a real acceptance condition, never a best-effort cleanup.
            # The old 'if regular' skipped selection while returning PASS and
            # later opened private-download consent dialogs in ordinary tests.
            self.ensure_regular()

    def journeys(self):
        self.click("More")
        self.click("Journeys")
        # Verify the actual native graph, not a transient More menu label.
        until = time.monotonic() + 12
        while self.find("Search pages and branches") is None:
            if time.monotonic() >= until:
                raise AssertionError("Journeys graph editor did not replace webpage")
            time.sleep(.6)
        if self.find("My browsing") is None:
            raise AssertionError("Native graph lacks default persisted journey")
        return "Opened native Journey graph with search and persisted rows"

    def upload(self):
        """Select a synthetic file through Android DocumentsUI and verify exact posted bytes."""
        from wave2_fixture import UPLOAD_BYTES
        self.ensure_regular()  # SAF may not be evaluated as ordinary after private QA.
        filename = "night-qa-upload.txt"
        local = self.out / filename
        local.write_bytes(UPLOAD_BYTES)
        # The file belongs to this disposable emulator, never to the user's device.
        adb("push", str(local), "/sdcard/Download/" + filename)
        shell("am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
              "-d", "file:///sdcard/Download/" + filename, check=False)
        self.go("/upload")
        before = hits("/upload/submit")
        self.click("Choose File", partial=True, timeout=15)
        # The live chooser is owned by Android DocumentsUI; never use an app-internal
        # file path or shortcut to claim that the real SAF upload flow passed.
        if self.find(filename, partial=True) is None:
            for label in ("Browse", "Show roots", "Open navigation drawer"):
                if self.find(label, partial=True) is not None:
                    self.click(label, partial=True)
                    break
            if self.find("Downloads", partial=True) is not None:
                self.click("Downloads", partial=True)
        if self.find(filename, partial=True) is None:
            raise Blocked("Android SAF chooser could not locate synthetic Downloads test file")
        self.click(filename, partial=True, timeout=15)
        # Android returns from DocumentsUI before Gecko has necessarily
        # attached the content URI to the DOM file input. Submitting during
        # that transition produces an empty multipart file part (SHA e3b0...).
        # Demand an actual visible selected filename in the webpage, not just
        # that the system picker closed, before sending the request.
        until = time.monotonic() + 16
        while self.find(filename, partial=True) is None:
            if time.monotonic() >= until:
                raise AssertionError("SAF returned but Gecko file input never displayed selected filename")
            time.sleep(.6)
        self.click("Upload test file", partial=True, timeout=15)
        wait_hit("/upload/submit", before)
        expected_sha = hashlib.sha256(UPLOAD_BYTES).hexdigest()
        actual_sha = fixture().get("upload_sha256")
        if actual_sha != expected_sha:
            observation = fixture()
            raise AssertionError(
                "SAF multipart bytes mismatch: expected=" + expected_sha[:12]
                + " actual=" + str(actual_sha)[:12]
                + " payload-bytes=" + str(observation.get("upload_length"))
                + " submits=" + str(observation.get("count", {}).get("/upload/submit", 0)))
        return "Gecko/SAF multipart upload bytes matched SHA256 on fixture server"

    @staticmethod
    def app_download_names():
        """Only the app's sandboxed, UUID-prefixed finished Gecko files."""
        listing = adb("shell", "run-as", PKG, "ls", "files/browser-downloads",
                      check=False)
        return {name.strip() for name in listing.splitlines()
                if re.fullmatch(r"[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}_.+",
                                name.strip())}

    def download(self, path):
        # This is deliberately a FULL byte-verified download acceptance, not a
        # pass for merely issuing GET or opening a consent dialog.
        from wave2_fixture import PNG, PDF, BIN, WAV
        expected = {"/file.png": ("night-qa.png", PNG),
                    "/file.pdf": ("night-qa.pdf", PDF),
                    "/secure/file.bin": ("night-secure-qa.bin", BIN),
                    "/media.wav": ("night-qa-audio.wav", WAV)}
        if path not in expected:
            raise AssertionError("No reference fixture for download")
        filename, original = expected[path]
        self.ensure_regular()  # App-owned Library downloads must never use incognito.
        prior = self.app_download_names()
        self.go(path)
        self.click("Save file", timeout=25)
        end = time.monotonic() + 35
        observed = None
        while time.monotonic() < end:
            candidates = (self.app_download_names() - prior)
            for name in sorted(candidates):
                if not name.endswith("_" + filename):
                    continue
                # Execute in the debug APK's app UID. No root, broad-storage
                # permissions, or user files are involved.
                data = adb("exec-out", "run-as", PKG, "cat",
                           "files/browser-downloads/" + name,
                           binary=True, check=False)
                if data and hashlib.sha256(data).digest() == hashlib.sha256(original).digest():
                    observed = (name, len(data))
                    break
            if observed is not None:
                break
            time.sleep(1)
        if observed is None:
            raise AssertionError("Downloaded fixture bytes not present or SHA256 mismatch: " + filename)
        self.click("More")
        self.click("Library")
        self.click("Downloads")
        if self.find(filename, partial=True) is None:
            raise AssertionError("Byte-verified download missing from Library: " + filename)
        return ("Original Gecko response bytes verified SHA256; Library shows "
                + filename + " (" + str(observed[1]) + " bytes)")

    def authenticated_download(self):
        """Cookie-bearing Gecko original-response bytes, never a bare GET pass."""
        before = fixture()["secure_download_ok"]
        self.go("/cookie/set")
        self.go("/cookie/echo")
        echoes = list(fixture()["cookie_seen"].values())
        if not echoes or not echoes[-1]:
            raise AssertionError("Normal Gecko profile did not send the control cookie")
        outcome = self.download("/secure/file.bin")
        after = fixture()["secure_download_ok"]
        if after <= before:
            raise AssertionError("Protected endpoint never saw the Gecko session cookie")
        return outcome + "; cookie-authenticated fixture response confirmed"

    def duplicate_download(self):
        """Never silently overwrite or re-transfer a completed previous file."""
        from wave2_fixture import PNG
        previous = self.app_download_names()
        if not any(name.endswith("_night-qa.png") for name in previous):
            raise AssertionError("No original completed PNG to duplicate")
        self.go("/file.png")
        deadline = time.monotonic() + 20
        while self.find("Download again?") is None:
            if time.monotonic() >= deadline:
                raise AssertionError("Repeat download did not ask before saving")
            time.sleep(.7)
        self.click("Cancel")
        time.sleep(1.5)
        if self.app_download_names() != previous:
            raise AssertionError("Cancelled repeated download wrote another file")
        self.go("/file.png")
        self.click("Download again", timeout=20)
        until = time.monotonic() + 35
        extra = set()
        while time.monotonic() < until:
            extra = self.app_download_names() - previous
            if extra:
                break
            time.sleep(.8)
        if not extra:
            raise AssertionError("Confirmed repeat download saved no second file")
        if len(extra) != 1:
            raise AssertionError("Repeated download added unexpected extra files")
        newer = next(iter(extra))
        for name in [newer, next(name for name in previous
                                  if name.endswith("_night-qa.png"))]:
            raw = adb("exec-out", "run-as", PKG, "cat",
                      "files/browser-downloads/" + name,
                      binary=True, check=False)
            if hashlib.sha256(raw).digest() != hashlib.sha256(PNG).digest():
                raise AssertionError("Repeat changed original or new bytes")
        return "Cancel wrote zero files; confirmation produced distinct exact PNG bytes"

    def media_duplicate(self):
        """Audio uses the same consent path and never silently saves twice."""
        self.download("/media.wav")
        before = self.app_download_names()
        self.go("/media.wav")
        deadline = time.monotonic() + 20
        while self.find("Download again?") is None:
            if time.monotonic() >= deadline:
                raise AssertionError("Previously downloaded audio did not warn")
            time.sleep(.7)
        self.click("Cancel")
        if self.app_download_names() != before:
            raise AssertionError("Cancelled repeat audio modified saved files")
        return "Actual WAV bytes saved; repeat audio warned; cancel retained one copy"

    def duplicate_after_restart(self):
        """Completed normal downloads must be remembered after process restart."""
        before = self.app_download_names()
        if len([name for name in before if name.endswith("_night-qa.png")]) < 2:
            raise AssertionError("Expected two completed original copies from C40")
        shell("am", "force-stop", PKG)
        shell("am", "start", "-W", "-n", PKG + "/.MainActivity")
        self.go("/file.png")
        deadline = time.monotonic() + 20
        while self.find("Download again?") is None:
            if time.monotonic() >= deadline:
                raise AssertionError("After restart prior download was forgotten")
            time.sleep(.7)
        self.click("Cancel")
        if self.app_download_names() != before:
            raise AssertionError("Restart/cancel changed completed saved files")
        return "Repeat confirmation survived app restart without downloading"

    def time_machine(self):
        self.ensure_regular()  # Persisted PDF capture is deliberately unavailable in private.
        self.go("/landing")
        # Fixture HTTP requests may arrive before Gecko paints the committed page.
        self.verify_title("NIGHT QA REDIRECT LANDING")
        self.click("More")
        self.click("Save PDF copy")
        self.click("More")
        self.click("Time Machine")
        # A saved PDF must be real bytes plus durable archive metadata. Some
        # Compose accessibility trees merge the title row with the parent card,
        # so looking for only its text produced a false FAIL despite a visible
        # 10 KB thumbnail and an actual indexed copy on API36.
        deadline = time.monotonic() + 28
        diagnostic = "archive metadata unavailable"
        while time.monotonic() < deadline:
            try:
                xml = adb("exec-out", "run-as", PKG, "cat",
                          "shared_prefs/night_time_machine_v2.xml",
                          binary=True, check=False)
                root = ElementTree.fromstring(xml)
                entry = root.find(".//string[@name='versions']")
                versions = json.loads(entry.text) if entry is not None and entry.text else []
                for item in versions:
                    name = item.get("file", "")
                    if (item.get("url") != URL + "/landing" or
                            item.get("title") != "NIGHT QA REDIRECT LANDING" or
                            not re.fullmatch(r"page-[0-9]+-[0-9a-f]{8}\.pdf", name)):
                        continue
                    data = adb("exec-out", "run-as", PKG, "cat",
                               "files/saved-pages/" + name,
                               binary=True, check=False)
                    if (len(data) == item.get("bytes") and len(data) >= 1000 and
                            data.startswith(b"%PDF-") and b"%%EOF" in data[-1024:]):
                        return ("Verified indexed landing URL/title and "
                                + str(len(data)) + " original saved PDF bytes on device")
                    diagnostic = "indexed PDF size/header mismatch"
                if not versions:
                    diagnostic = "archive has no indexed versions yet"
                elif diagnostic == "archive metadata unavailable":
                    diagnostic = "indexed version differs from expected landing page"
            except (ValueError, ElementTree.ParseError, TypeError):
                diagnostic = "archive index not yet available"
            time.sleep(1)
        raise AssertionError("Real Time Machine PDF capture not verified: " + diagnostic)

    def peek(self):
        self.go("/links")
        self.click("Visit A", long=True)
        self.click("Peek")
        self.click("Dismiss preview")
        return "Long-press opened disposable preview and dismissed it"

    def offline_pdf(self):
        """Decode a captured PDF page using native PdfRenderer without a network."""
        self.click("Home")
        self.click("More")
        self.click("Time Machine")
        if self.find("Open PDF version") is None:
            raise AssertionError("No verified saved PDF action in Time Machine")
        shell("svc", "wifi", "disable", check=False)
        shell("svc", "data", "disable", check=False)
        try:
            self.click("Open PDF version")
            deadline = time.monotonic() + 24
            while self.find("Saved PDF page 1") is None:
                if time.monotonic() >= deadline:
                    raise AssertionError("Offline PDF page pixels were not rendered")
                time.sleep(1)
            self.img("C34-rendered-pdf")
            self.click("Close saved PDF")
            return "Saved PDF decoded to first-page pixels while network disabled"
        finally:
            shell("svc", "wifi", "enable", check=False)
            shell("svc", "data", "enable", check=False)

    def mem(self):
        raw = shell("dumpsys", "meminfo", PKG, check=False)
        total = re.search(r"TOTAL\s+PSS:\s*([\d,]+)", raw)
        alt = re.search(r"TOTAL\s+([\d,]+)", raw)
        if not (total or alt):
            raise Blocked("dumpsys meminfo did not expose total PSS")
        return "PSS estimate " + (total or alt).group(1) + " KB; threshold not established"

    def home_visual(self):
        self.click("Home")
        tree = self.ui()
        if not any(n.get("text", "") == "Quick access" for n in tree):
            raise AssertionError("Home shortcuts section not exposed")
        # Capture the actual start page, not Gboard's first-run onboarding popover.
        # The user should not have to dismiss a keyboard just to see Home.
        if any("inputmethod" in n.get("package", "").casefold() for n in tree):
            raise AssertionError("Keyboard unexpectedly obscures cold Home")
        # UIAutomator sometimes omits Gboard's entire hierarchy on API26.
        # The IME itself is a separate Android system-window accessibility root.
        state = shell("dumpsys", "input_method", check=False)
        if "mInputShown=true" in state or "mShowRequested=true" in state:
            raise AssertionError("IME visible over cold Home")
        return "Home shortcuts visible with IME hidden"

    def offline_home(self):
        shell("svc", "wifi", "disable", check=False)
        shell("svc", "data", "disable", check=False)
        try:
            self.click("Home")
            if self.find("Quick access") is None:
                raise AssertionError("Home lost Quick access offline")
            return "App home accessible with Android network toggled off"
        finally:
            shell("svc", "wifi", "enable", check=False)
            shell("svc", "data", "enable", check=False)

    def finish(self, gate=False):
        logs = adb("logcat", "-d", "-t", "3000", check=False)
        matched = [line[:350] for line in logs.splitlines() if
                   ("FATAL EXCEPTION" in line or "Process: " + PKG in line)
                   and "AndroidRuntime" in line]
        self.rows.append({"id": "C30", "case": "Filtered fatal runtime diagnostics",
                          "status": "FAIL" if any("Process: " + PKG in x for x in matched) else "PASS",
                          "seconds": 0, "evidence": "; ".join(matched[-8:]),
                          "screenshot": None})
        digest = None
        if self.apk:
            with open(self.apk, "rb") as f:
                h = hashlib.sha256()
                for chunk in iter(lambda: f.read(1024 * 1024), b""):
                    h.update(chunk)
                digest = h.hexdigest()
        report = {"date_utc": self.boot, "integration_sha": os.getenv("GITHUB_SHA", "local-unknown"),
                  "apk_sha256": digest, "api": self.api, "model": self.model,
                  "emulator_serial": self.serial, "viewport": self.size,
                  "test_data": "fresh disposable profile; synthetic pages only",
                  "physical_device_tested": False, "cases": self.rows}
        (self.out / "report.json").write_text(json.dumps(report, indent=2) + "\n")
        lines = ["# NIGHT Browser Wave-2 independent Android QA", "",
                 "Integration SHA: " + report["integration_sha"],
                 "Android API " + self.api + " / " + self.model + " / " + self.size,
                 "App data wiped in disposable emulator. Device screenshots are NOT manually reviewed.",
                 "PASS is a verified narrow assertion, not certification of the full named feature.",
                 "NOT_RUN/BLOCKED/FAIL cannot count as release acceptance.", "",
                 "| ID | Scenario | Status | Evidence |", "|---|---|---|---|"]
        for row in self.rows:
            ev = row["evidence"].replace("|", "/").replace("\n", " ")[:220]
            lines.append("| " + row["id"] + " | " + row["case"] + " | "
                         + row["status"] + " | " + ev + " |")
        lines.extend(["", "## Release blockers", "",
            "External real-site login, authenticated download, camera/microphone permission, "
            "AI provider consent, Site Studio per-origin isolation, independent PDF decode review, "
            "tab thumbnail pixels, scroll jank, slow network, process death, thermal/battery "
            "and physical phone require explicit complementary evidence. "
            "This test run does not certify any of them.", ""])
        (self.out / "report.md").write_text("\n".join(lines))
        fails = sum(r["status"] == "FAIL" for r in self.rows)
        passed = sum(r["status"] == "PASS" for r in self.rows)
        skipped = len(self.rows) - fails - passed
        print("[WAVE2] " + str(passed) + " PASS / " + str(fails) + " FAIL / "
              + str(skipped) + " NOT_RUN,BLOCKED", flush=True)
        return 1 if gate and (fails or skipped) else 0

    def run(self, gate=False):
        fixture()  # fail early when fixture not reachable
        adb("wait-for-device", timeout=140)
        adb("logcat", "-c", check=False)
        self.test("C01", "Cold Activity launch", self.cold_start)
        self.test("C02", "Home layout without unwanted keyboard", self.home_visual,
                  prerequisites=("C01",), screenshot=True)
        self.test("C03", "Real external HTTPS website", self.external, prerequisites=("C01",))
        self.test("C04", "Gecko local website loads", lambda: self.go("/"), prerequisites=("C01",), screenshot=True)
        self.test("C05", "Gecko follows HTTP redirect", self.redirect, prerequisites=("C04",))
        self.test("C06", "HTTP 404 error page", lambda: self.go("/not-found"), prerequisites=("C04",))
        self.test("C07", "HTTP 500 error page", lambda: self.go("/server-error"), prerequisites=("C04",))
        self.test("C08", "HTML links page", lambda: self.go("/links"), prerequisites=("C04",))
        self.test("C09", "Touch link A", lambda: self.link("/branch/a", "Visit A"),
                  prerequisites=("C08",), screenshot=True)
        self.test("C10", "Touch child link C", lambda: self.link("/branch/c", "Visit C"),
                  prerequisites=("C09",))
        self.test("C11", "Back returns to branch A", lambda: (
                  self.click("Back"), self.verify_title("NIGHT QA BRANCH A")),
                  prerequisites=("C10",), screenshot=True)
        self.test("C12", "Forward returns to branch C", lambda: (
                  self.click("Forward"), self.verify_title("NIGHT QA BRANCH C")),
                  prerequisites=("C11",))
        self.test("C13", "Bookmark test site", lambda: (
                  self.go("/"), self.click("More"), self.click("Bookmark page")),
                  prerequisites=("C04",))
        self.test("C14", "Library bookmark visible", lambda: (
                  self.click("More"), self.click("Library"),
                  self.click("10.0.2.2", partial=True)), prerequisites=("C13",))
        self.test("C15", "Journeys history access", self.journeys,
                  prerequisites=("C13",), screenshot=True)
        self.test("C16", "10 tabs via touch", self.ten_tabs, prerequisites=("C01",), screenshot=True)
        self.test("C17", "Landscape UI still interactive", self.rotate,
                  prerequisites=("C16",), screenshot=True)
        self.test("C18", "Tab restore after forced restart", self.restart,
                  prerequisites=("C16",), screenshot=True)
        self.test("C19", "Create real private tab", self.private, prerequisites=("C18",))
        self.test("C20", "Private cookie isolation", self.cookie_isolation,
                  prerequisites=("C19",), screenshot=True)
        self.test("C21", "HTML form GET", lambda: self.go("/form"),
                  prerequisites=("C01",))
        self.test("C22", "Submit real test form", lambda: (
                  self.click("Send test form"),
                  self._posted()), prerequisites=("C21",))
        self.test("C23", "File upload picker and bytes", self.upload,
                  prerequisites=("C01",), screenshot=True)
        self.test("C24", "PNG response bytes and Library entry", lambda: self.download("/file.png"),
                  prerequisites=("C01",), screenshot=True)
        self.test("C25", "PDF response bytes and Library entry", lambda: self.download("/file.pdf"),
                  prerequisites=("C01",), screenshot=True)
        self.test("C40", "Repeat PNG download confirmation and cancel",
                  self.duplicate_download, prerequisites=("C24",))
        self.test("C41", "Repeat warning survives application restart",
                  self.duplicate_after_restart, prerequisites=("C40",))
        self.test("C42", "Repeat WAV media download confirmation",
                  self.media_duplicate, prerequisites=("C01",), screenshot=True)
        self.test("C26", "Time Machine PDF version saved", self.time_machine,
                  prerequisites=("C01",), screenshot=True)
        self.test("C27", "Peek link long-press dismiss", self.peek,
                  prerequisites=("C01",), screenshot=True)
        self.test("C28", "Offline Home interactive", self.offline_home,
                  prerequisites=("C01",))
        self.test("C29", "Live process PSS captured", self.mem, prerequisites=("C01",))
        self.test("C31", "AI provider opt-in and response",
                  lambda: self._unverified("Test key, consent packet trace, grounded response and opt-out required"))
        self.test("C32", "Site Studio per-origin isolation",
                  lambda: self._unverified("Use two origins, CSS override, rollback, private exclusion"))
        self.test("C33", "Camera/mic prompt deny/allow/revoke",
                  lambda: self._unverified("Requires getUserMedia, OS prompt and permission-revocation assertion"))
        self.test("C34", "Open saved PDF offline", self.offline_pdf,
                  prerequisites=("C26",), screenshot=True)
        self.test("C35", "Authenticated download cookie and SHA256",
                  self.authenticated_download, prerequisites=("C04",))
        self.test("C36", "Real thumbnail quality",
                  lambda: self._unverified("Nonblank thumbnail screenshots must be reviewed"))
        self.test("C37", "Scroll jank and frame timing",
                  lambda: self._unverified("Requires swipe frame timing trace, p95 responsiveness"))
        self.test("C38", "Low-memory process death recovery",
                  lambda: self._unverified("Force-stop alone does not replicate Android LMK"))
        self.test("C39", "Physical-device API26/API36",
                  lambda: self._unverified("CI emulators only; no physical phone tested"))
        return self.finish(gate)

    @staticmethod
    def _missing(message):
        raise AssertionError(message)

    @staticmethod
    def _unverified(message):
        raise Blocked(message)

    @staticmethod
    def _posted():
        if not fixture()["form_posted"]:
            raise AssertionError("Fixture did not receive form POST")
        return "Fixture received POST from real Gecko form"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="night-browser/qa/results/wave2")
    parser.add_argument("--apk", help="Built APK for SHA-256 release-matrix evidence")
    parser.add_argument("--gate", action="store_true",
                        help="Fail CI on any failed, blocked or not-run case; release gate only")
    args = parser.parse_args()
    try:
        return Device(args.out, apk=args.apk).run(args.gate)
    except Exception as exc:
        print("[WAVE2] QA harness failure: " + repr(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
