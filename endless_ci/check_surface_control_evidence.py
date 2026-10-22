#!/usr/bin/env python3
"""Reject bogus/missing Android movement QA artifacts after instrumentation.

The input must be actual screenshots and process telemetry pulled from an
installed device/emulator. Never fall back to a generated mockup. No
performance threshold is inferred from a software-rendered emulator.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import struct


def dimensions(png: Path) -> tuple[int, int]:
    raw = png.read_bytes()
    if len(raw) < 10000 or raw[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"Missing or invalid PNG bytes: {png}")
    if raw[12:16] != b"IHDR":
        raise ValueError(f"No PNG header: {png}")
    w, h = struct.unpack(">II", raw[16:24])
    if not 500 <= w <= 10000 or not 250 <= h <= 10000:
        raise ValueError(f"Implausible device screenshot size {w}x{h}: {png}")
    return w, h


def validate(root: Path, sdk: int) -> None:
    shot = root / "controls-two-thumb-moon.png"
    telemetry = root / "controls-qa-device.txt"
    memory = root / "controls-meminfo-active.txt"
    gfx = root / "controls-gfxinfo-active.txt"
    width, height = dimensions(shot)
    if not telemetry.is_file():
        raise FileNotFoundError(telemetry)
    data = {}
    for line in telemetry.read_text().splitlines():
        if "=" in line:
            key, val = line.split("=", 1)
            data[key] = val
    if int(data.get("sdk", "-1")) != sdk:
        raise ValueError(f"Wrong SDK, expected {sdk}: {data.get('sdk')}")
    for field in ("completedFrames", "elapsedMs", "processPssKiB", "touchEvents"):
        if float(data.get(field, "0")) <= 0:
            raise ValueError(f"Missing live Android telemetry: {field}")
    for field in ("touchDispatchMedianMs", "touchDispatchMaxMs"):
        value = float(data.get(field, "-1"))
        if not 0 <= value < 60000:
            raise ValueError(f"Invalid touch dispatch telemetry: {field}={value}")
    if "not a benchmark" not in data.get("method", ""):
        raise ValueError("Do not claim emulator activity-inclusive sample is a GPU benchmark")
    for diagnostics in (memory, gfx):
        if not diagnostics.is_file() or diagnostics.stat().st_size < 20:
            raise FileNotFoundError(f"No live Android process diagnostics: {diagnostics}")
    if "No process found" in memory.read_text():
        raise ValueError("Android process terminated before live memory captured")
    print(f"PASS actual Android API {sdk} movement QA: {width}x{height} screen, "
          f"{data['touchEvents']} routed touches, live process diagnostics")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("screenshots_dir", type=Path)
    parser.add_argument("--sdk", required=True, type=int, choices=(26, 36))
    args = parser.parse_args()
    validate(args.screenshots_dir, args.sdk)
