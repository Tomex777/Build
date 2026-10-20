#!/usr/bin/env python3
"""Independently verify that two *real captured* Mise viewport frames changed.

This is a narrow automated regression gate, not a substitute for human visual QA.
No image libraries or Android emulator are needed to inspect the saved PNG proof.
"""

import argparse
import hashlib
import json
from pathlib import Path
import statistics
import struct
import sys
import zlib

PNG_MAGIC = b"\x89PNG\r\n\x1a\n"
# Exclude toolbars/sheets around the central 3D canvas (fraction of whole frame).
REGION = (0.20, 0.18, 0.80, 0.58)


class EvidenceError(ValueError):
    """The screenshots do not establish the claimed visible scene change."""


def png_samples(path: Path):
    """Return dimensions and center-of-viewport RGB samples; support PNG RGB/RGBA."""
    data = path.read_bytes()
    if not data.startswith(PNG_MAGIC):
        raise EvidenceError(f"{path}: not a PNG")
    offset = len(PNG_MAGIC)
    compressed = bytearray()
    width = height = channels = None
    saw_end = False
    while offset + 12 <= len(data):
        size = struct.unpack_from(">I", data, offset)[0]
        kind = data[offset + 4:offset + 8]
        end = offset + 12 + size
        if end > len(data):
            raise EvidenceError(f"{path}: truncated PNG chunk")
        payload = data[offset + 8:offset + 8 + size]
        checksum = struct.unpack_from(">I", data, offset + 8 + size)[0]
        if zlib.crc32(kind + payload) & 0xFFFFFFFF != checksum:
            raise EvidenceError(f"{path}: PNG chunk CRC mismatch")
        if kind == b"IHDR":
            if width is not None:
                raise EvidenceError(f"{path}: duplicate PNG header")
            width, height, depth, color, compression, filtering, interlace = struct.unpack(">IIBBBBB", payload)
            if not (0 < width <= 8192 and 0 < height <= 8192):
                raise EvidenceError(f"{path}: unexpected PNG dimensions")
            if depth != 8 or color not in (2, 6) or compression or filtering or interlace:
                raise EvidenceError(f"{path}: requires non-interlaced 8-bit RGB/RGBA PNG")
            channels = 3 if color == 2 else 4
        elif kind == b"IDAT":
            compressed.extend(payload)
        elif kind == b"IEND":
            saw_end = True
            break
        offset = end
    if not saw_end or width is None:
        raise EvidenceError(f"{path}: incomplete PNG")
    stride = width * channels
    try:
        raw = zlib.decompress(compressed)
    except zlib.error as exc:
        raise EvidenceError(f"{path}: invalid PNG pixel stream: {exc}") from exc
    if len(raw) != (stride + 1) * height:
        raise EvidenceError(f"{path}: wrong PNG pixel byte count")

    left, top, right, bottom = REGION
    x0, x1 = int(width * left), int(width * right)
    y0, y1 = int(height * top), int(height * bottom)
    samples = []
    previous = bytearray(stride)
    for y in range(height):
        start = y * (stride + 1)
        mode = raw[start]
        row = bytearray(raw[start + 1:start + 1 + stride])
        if mode not in range(5):
            raise EvidenceError(f"{path}: invalid PNG scanline filter {mode}")
        for i in range(stride):
            left_pixel = row[i - channels] if i >= channels else 0
            up = previous[i]
            upper_left = previous[i - channels] if i >= channels else 0
            if mode == 1:
                predictor = left_pixel
            elif mode == 2:
                predictor = up
            elif mode == 3:
                predictor = (left_pixel + up) // 2
            elif mode == 4:
                p = left_pixel + up - upper_left
                a, b, c = abs(p - left_pixel), abs(p - up), abs(p - upper_left)
                predictor = left_pixel if a <= b and a <= c else up if b <= c else upper_left
            else:
                predictor = 0
            row[i] = (row[i] + predictor) & 255
        if y0 <= y < y1 and y % 4 == 0:
            for x in range(x0, x1, 4):
                pos = x * channels
                samples.append(tuple(row[pos:pos + 3]))
        previous = row
    if not samples:
        raise EvidenceError(f"{path}: screenshot too small for viewport analysis")
    return width, height, samples


def compare(before: Path, after: Path, *, threshold: int = 28, minimum_pct: float = 0.25):
    width, height, a = png_samples(before)
    new_width, new_height, b = png_samples(after)
    if (width, height) != (new_width, new_height):
        raise EvidenceError("Screenshot dimensions changed; cannot compare scene pixels")
    for path, pixels in ((before, a), (after, b)):
        nonblack = sum(max(rgb) > 18 for rgb in pixels) / len(pixels)
        luminance = [sum(rgb) / 3 for rgb in pixels]
        if nonblack < .10 or statistics.pstdev(luminance) < 3:
            raise EvidenceError(f"{path}: blank, black, or uniform 3D viewport")
    changed = sum(max(abs(x - y) for x, y in zip(rgb_a, rgb_b)) >= threshold
                  for rgb_a, rgb_b in zip(a, b))
    changed_pct = changed / len(a) * 100
    result = {
        "before": str(before), "after": str(after),
        "before_sha256": hashlib.sha256(before.read_bytes()).hexdigest(),
        "after_sha256": hashlib.sha256(after.read_bytes()).hexdigest(),
        "dimensions": [width, height], "viewport_samples": len(a),
        "changed_samples": changed, "changed_percent": round(changed_pct, 3),
        "color_delta_threshold": threshold, "minimum_changed_percent": minimum_pct,
        "passed": changed_pct >= minimum_pct,
    }
    if not result["passed"]:
        raise EvidenceError(
            f"{before.name} -> {after.name}: only {changed_pct:.3f}% of central "
            f"viewport pixels changed (required {minimum_pct:.3f}%); static pose/action?"
        )
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--before", type=Path, required=True)
    parser.add_argument("--after", type=Path, required=True)
    parser.add_argument("--case", required=True, help="Human-readable scene action label")
    parser.add_argument("--json", type=Path, required=True, help="Proof metrics saved with real screenshots")
    parser.add_argument("--minimum-pct", type=float, default=0.25,
                        help="Minimum percent of central viewport samples with changed RGB values")
    parser.add_argument("--color-delta", type=int, default=28)
    args = parser.parse_args()
    if not (0 < args.minimum_pct <= 100 and 1 <= args.color_delta <= 255):
        parser.error("Expected 0 < minimum-pct <= 100 and 1 <= color-delta <= 255")
    try:
        result = compare(args.before, args.after, threshold=args.color_delta,
                         minimum_pct=args.minimum_pct)
        result["case"] = args.case
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        print(f"ACCEPTED {args.case}: {result['changed_percent']:.3f}% central pixels changed; "
              f"independent real-frame comparison in {args.json}")
    except (OSError, EvidenceError, struct.error) as exc:
        sys.exit(f"QA FRAME FAIL [{args.case}]: {exc}")


if __name__ == "__main__":
    main()
