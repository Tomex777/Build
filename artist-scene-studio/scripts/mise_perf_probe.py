#!/usr/bin/env python3
"""Capture honest Android memory and UI-frame evidence for a running Mise scene.

Requires Python 3 and adb. This is a diagnostic capture, not a GPU FPS benchmark:
Android gfxinfo does not necessarily include Filament SurfaceView frames.
"""
from __future__ import annotations

import argparse
import csv
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import subprocess


def parse_total_pss_kib(meminfo: str) -> int | None:
    """Support both old TOTAL table rows and modern TOTAL PSS summaries."""
    summary = re.search(r"^\s*TOTAL PSS:\s*([\d,]+)", meminfo, re.MULTILINE)
    if summary:
        return int(summary.group(1).replace(",", ""))
    for line in meminfo.splitlines():
        row = re.match(r"^\s*TOTAL\s+([\d,]+)(?:\s|$)", line)
        if row:
            return int(row.group(1).replace(",", ""))
    return None


def parse_ui_frames_ms(gfxinfo: str) -> list[float]:
    """Use completed Android UI frames only; ignore incomplete/invalid rows."""
    rows: list[float] = []
    in_profile = False
    columns: dict[str, int] = {}
    for line in gfxinfo.splitlines():
        line = line.strip()
        if line == "---PROFILEDATA---":
            in_profile = not in_profile
            columns = {}
            continue
        if not in_profile or not line:
            continue
        values = next(csv.reader([line]))
        if values[0] == "Flags":
            columns = {name: i for i, name in enumerate(values)}
            continue
        if not all(k in columns for k in ("Flags", "IntendedVsync", "FrameCompleted")):
            continue
        try:
            if int(values[columns["Flags"]]) != 0:
                continue
            intended = int(values[columns["IntendedVsync"]])
            completed = int(values[columns["FrameCompleted"]])
        except (ValueError, IndexError):
            continue
        if completed <= intended or intended <= 0:
            continue
        duration = (completed - intended) / 1_000_000
        if 0 < duration < 100_000:
            rows.append(round(duration, 3))
    return rows


def percentile(values: list[float], pct: float) -> float | None:
    if not values:
        return None
    ranked = sorted(values)
    index = max(0, min(len(ranked) - 1, -(-int(pct * len(ranked) * 1000) // 1000) - 1))
    return round(ranked[index], 3)


def summarize(meminfo: str, gfxinfo: str) -> dict:
    durations = parse_ui_frames_ms(gfxinfo)
    return {
        "totalPssKiB": parse_total_pss_kib(meminfo),
        "uiFrameSamples": len(durations),
        "uiCompletionMsP50": percentile(durations, 0.50),
        "uiCompletionMsP95": percentile(durations, 0.95),
        "uiFramesOver16_67Ms": sum(d > 16.67 for d in durations),
        "uiFramesOver33_33Ms": sum(d > 33.33 for d in durations),
        "caveat": (
            "UI completion times from Android gfxinfo, not Filament GPU frame times or FPS. "
            "Scene complexity and thermal behavior must be profiled separately."
        ),
    }


def adb(*args: str, optional: bool = False) -> str:
    try:
        result = subprocess.run(
            ["adb", *args], check=True, capture_output=True, text=True, timeout=35
        )
        return result.stdout
    except (FileNotFoundError, subprocess.CalledProcessError, subprocess.TimeoutExpired):
        if optional:
            return "unavailable\n"
        raise


def capture(package: str, output_dir: Path) -> dict:
    if adb("get-state").strip() != "device":
        raise RuntimeError("ADB has no connected Android device")
    pid = adb("shell", "pidof", package).strip().replace("\r", "")
    if not pid or not all(part.isdigit() for part in pid.split()):
        raise RuntimeError(f"{package} is not running; cannot claim performance evidence")

    # Device data is captured without modifying scene or app state.
    meminfo = adb("shell", "dumpsys", "meminfo", package)
    gfxinfo = adb("shell", "dumpsys", "gfxinfo", package, "framestats")
    sdk = adb("shell", "getprop", "ro.build.version.sdk").strip()
    abi = adb("shell", "getprop", "ro.product.cpu.abi").strip()
    thermal = adb("shell", "dumpsys", "thermalservice", optional=True)

    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "meminfo.txt").write_text(meminfo, encoding="utf-8")
    (output_dir / "gfxinfo.txt").write_text(gfxinfo, encoding="utf-8")
    (output_dir / "thermalservice.txt").write_text(thermal, encoding="utf-8")
    result = {
        "package": package,
        "pid": pid,
        "apiLevel": sdk,
        "abi": abi,
        "capturedAtUtc": datetime.now(timezone.utc).isoformat(),
        **summarize(meminfo, gfxinfo),
    }
    (output_dir / "summary.json").write_text(
        json.dumps(result, sort_keys=True, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(result, sort_keys=True))
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package", default="studio.artistscene.app")
    parser.add_argument("--output-dir", required=True, type=Path)
    args = parser.parse_args()
    capture(args.package, args.output_dir)
