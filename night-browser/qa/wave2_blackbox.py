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
        fields = [n for n in self.ui() if n.get("class") == "android.widget.EditText"
                  or n.get("editable") == "true"]
        if not fields:
            raise AssertionError("Omnibar not exposed as an editable field")
        def key(node):
            b = list(map(int, re.findall(r"\d+", node.get("bounds", ""))))
            return (b[1] + b[3]) if len(b) == 4 else -1
        field = max(fields, key=key)
        b = list(map(int, re.findall(r"\d+", field.get("bounds", ""))))
        shell("input", "tap", str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2))
        time.sleep(.3)
        current = field.get("text", "")
        if current:
            shell("input", "keyevent", "123", *(["67"] * min(len(current) + 4, 320)))
        shell("input", "text", raw)
        time.sleep(.3)
        if not any(raw in n.get("text", "") for n in self.ui()
                   if n.get("class") == "android.widget.EditText"):
            raise AssertionError("Typed URL not reflected in omnibar")

    def go(self, path):
        # Navigates through real on-screen controls. Do not call Gecko internals.
        before = hits(path)
        self.click("Home")
        self.edit(URL + path)
        if self.find("Go to address or search"):
            self.click("Go to address or search")
        else:
            self.click("Go")
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
        self.click("Go to address or search" if self.find("Go to address or search") else "Go")
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
        self.click("Tabs")
        # Gecko title delivery is asynchronous after Back/Forward.
        deadline = time.monotonic() + 18
        while not self.find(title, partial=True):
            if time.monotonic() >= deadline:
                raise AssertionError("Expected committed title not shown: " + title)
            time.sleep(.6)
        self.click(title, partial=True)
        return "Confirmed committed tab title " + title

    def tab_count(self):
        self.click("Tabs")
        return [n.get("text", "") or n.get("content-desc", "") for n in self.ui()]

    def ten_tabs(self):
        labels = self.tab_count()
        matches = [re.search(r"(?:Regular\s+(\d+)|^(\d+)$)", x) for x in labels]
        numbers = [int(m.group(1) or m.group(2)) for m in matches if m]
        if not numbers:
            raise AssertionError("Regular tab count was not exposed")
        initial = max(numbers)
        if initial > 10:
            raise Blocked("Already >10 tabs; isolated reset required")
        for _ in range(initial, 10):
            self.click("New tab")
            self.click("Tabs")
        labels = self.tab_count()
        if not any(re.search(r"(?:Regular\s+10|^10$)", x) for x in labels):
            raise AssertionError("10 regular tabs not visible in overview")
        return "Created up to ten tabs through actual controls"

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
        labels = self.tab_count()
        if not any(re.search(r"(?:Regular\s+10|^10$)", x) for x in labels):
            raise AssertionError("Tab count not restored after force-stop")
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
            # Return to regular browsing so PDF/history tests don't write
            # snapshots or form data into a private session.
            self.click("Tabs")
            regular = next((n for n in self.ui() if re.fullmatch(
                r"Regular\s+\d+", n.get("text", "") or n.get("content-desc", ""))), None)
            if regular:
                label = regular.get("text", "") or regular.get("content-desc", "")
                self.click(label)
                self.click("New tab")

    def upload(self):
        self.go("/upload")
        self.click("Choose File", partial=True, timeout=8)
        raise Blocked("File picker launched: requires OS picker navigation + byte assertion; not a verified upload")

    def download(self, path):
        self.go(path)
        raise Blocked("GET logged, but saved bytes/MIME/open/delete must be verified in app's Library")

    def time_machine(self):
        self.go("/landing")
        self.click("More")
        self.click("Save PDF copy")
        self.click("More")
        self.click("Time Machine")
        # Saving and indexing Gecko's PDF bytes is asynchronous on real devices.
        deadline = time.monotonic() + 24
        while not self.find("NIGHT QA REDIRECT LANDING", partial=True):
            if time.monotonic() >= deadline:
                raise AssertionError("PDF version not present after 24s")
            time.sleep(1)
        return "Save PDF action then named page version displayed; offline open separately pending"

    def peek(self):
        self.go("/links")
        self.click("Visit A", long=True)
        self.click("Peek")
        self.click("Dismiss preview")
        return "Long-press opened disposable preview and dismissed it"

    def mem(self):
        raw = shell("dumpsys", "meminfo", PKG, check=False)
        total = re.search(r"TOTAL\s+PSS:\s*([\d,]+)", raw)
        alt = re.search(r"TOTAL\s+([\d,]+)", raw)
        if not (total or alt):
            raise Blocked("dumpsys meminfo did not expose total PSS")
        return "PSS estimate " + (total or alt).group(1) + " KB; threshold not established"

    def offline_home(self):
        shell("svc", "wifi", "disable", check=False)
        shell("svc", "data", "disable", check=False)
        try:
            self.click("Home")
            if not self.find("Quick access"):
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
            "AI provider consent, Site Studio per-origin isolation, PDF offline open, "
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
        self.test("C02", "Quick access UI", lambda: self.click("Quick access")
                  if not self.find("Quick access") else "Quick access visible", prerequisites=("C01",),
                  screenshot=True)
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
                  prerequisites=("C10",))
        self.test("C12", "Forward returns to branch C", lambda: (
                  self.click("Forward"), self.verify_title("NIGHT QA BRANCH C")),
                  prerequisites=("C11",))
        self.test("C13", "Bookmark test site", lambda: (
                  self.go("/"), self.click("More"), self.click("Bookmark page")),
                  prerequisites=("C04",))
        self.test("C14", "Library bookmark visible", lambda: (
                  self.click("More"), self.click("Library"),
                  self.click("10.0.2.2", partial=True)), prerequisites=("C13",))
        self.test("C15", "Journeys history access", lambda: (
                  self.click("More"), self.click("Journeys"),
                  "Journeys panel visible" if self.find("Journeys", partial=True)
                  else self._missing("Journeys panel")), prerequisites=("C13",))
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
                  prerequisites=("C01",))
        self.test("C24", "PNG download saved/opened", lambda: self.download("/file.png"),
                  prerequisites=("C01",))
        self.test("C25", "PDF download saved/opened", lambda: self.download("/file.pdf"),
                  prerequisites=("C01",))
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
        self.test("C34", "Open saved PDF offline",
                  lambda: self._unverified("Listing a PDF isn't proof it opens without network"))
        self.test("C35", "Authenticated downloads verify byte SHA",
                  lambda: self._unverified("Requires login test fixture + download consent and SHA256"))
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
