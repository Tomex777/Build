#!/usr/bin/env python3
"""Compare the center of two PNG screenshots using only the Python stdlib."""

import struct
import sys
import zlib
from pathlib import Path

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def paeth(left: int, up: int, upper_left: int) -> int:
    estimate = left + up - upper_left
    left_distance = abs(estimate - left)
    up_distance = abs(estimate - up)
    upper_left_distance = abs(estimate - upper_left)
    if left_distance <= up_distance and left_distance <= upper_left_distance:
        return left
    if up_distance <= upper_left_distance:
        return up
    return upper_left


def center_mean(path: str) -> tuple[float, float, float]:
    payload = Path(path).read_bytes()
    if not payload.startswith(PNG_SIGNATURE):
        raise SystemExit(f"{path} is not a PNG screenshot")

    width = height = bit_depth = color_type = interlace = None
    compressed = bytearray()
    offset = len(PNG_SIGNATURE)
    while offset < len(payload):
        length = struct.unpack_from(">I", payload, offset)[0]
        kind = payload[offset + 4:offset + 8]
        data_start = offset + 8
        data_end = data_start + length
        data = payload[data_start:data_end]
        if kind == b"IHDR":
            width, height, bit_depth, color_type, compression, filtering, interlace = struct.unpack(
                ">IIBBBBB", data
            )
            if compression != 0 or filtering != 0:
                raise SystemExit("Unsupported PNG compression or filter method")
        elif kind == b"IDAT":
            compressed.extend(data)
        elif kind == b"IEND":
            break
        offset = data_end + 4  # Skip the chunk CRC.

    if width is None or height is None or bit_depth != 8 or interlace != 0:
        raise SystemExit("Expected a non-interlaced, 8-bit Android screenshot PNG")
    channels = {0: 1, 2: 3, 4: 2, 6: 4}.get(color_type)
    if channels is None:
        raise SystemExit(f"Unsupported PNG color type {color_type}")

    bytes_per_pixel = channels
    stride = width * bytes_per_pixel
    raw = zlib.decompress(compressed)
    x_start, x_end = width * 45 // 100, width * 55 // 100
    y_start, y_end = height * 45 // 100, height * 55 // 100
    totals = [0, 0, 0]
    samples = 0
    previous = bytearray(stride)
    offset = 0

    for y in range(height):
        filter_type = raw[offset]
        encoded = raw[offset + 1:offset + 1 + stride]
        offset += stride + 1
        row = bytearray(stride)
        if filter_type == 0:
            row[:] = encoded
        elif filter_type in (1, 2, 3, 4):
            for i, value in enumerate(encoded):
                left = row[i - bytes_per_pixel] if i >= bytes_per_pixel else 0
                up = previous[i]
                upper_left = previous[i - bytes_per_pixel] if i >= bytes_per_pixel else 0
                if filter_type == 1:
                    predictor = left
                elif filter_type == 2:
                    predictor = up
                elif filter_type == 3:
                    predictor = (left + up) // 2
                else:
                    predictor = paeth(left, up, upper_left)
                row[i] = (value + predictor) & 0xFF
        else:
            raise SystemExit(f"Unsupported PNG row filter {filter_type}")

        if y_start <= y < y_end:
            for x in range(x_start, x_end):
                i = x * bytes_per_pixel
                if color_type == 0:
                    pixel = (row[i], row[i], row[i])
                elif color_type == 2:
                    pixel = (row[i], row[i + 1], row[i + 2])
                elif color_type == 4:
                    pixel = (row[i], row[i], row[i])
                else:
                    pixel = (row[i], row[i + 1], row[i + 2])
                for channel in range(3):
                    totals[channel] += pixel[channel]
                samples += 1
        previous = row

    if samples == 0:
        raise SystemExit("Screenshot center crop was empty")
    return tuple(total / samples for total in totals)


if len(sys.argv) != 3:
    raise SystemExit("usage: assert_gif_animation.py <first.png> <second.png>")

first = center_mean(sys.argv[1])
second = center_mean(sys.argv[2])
mean_difference = sum(abs(a - b) for a, b in zip(first, second)) / 3
if mean_difference < 25:
    raise SystemExit(f"GIF frame screenshots did not change enough (mean RGB delta {mean_difference:.1f})")
print(f"Animated GIF frame change verified (mean RGB delta {mean_difference:.1f})")
