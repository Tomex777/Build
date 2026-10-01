#!/usr/bin/env python3
"""Reject a black viewport without counting editor bars as renderer output."""
import statistics
import struct
import sys
import zlib


def check(path):
    data = open(path, 'rb').read()
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        raise ValueError('Screenshot is not PNG')
    offset, compressed = 8, bytearray()
    while offset < len(data):
        size = struct.unpack('>I', data[offset:offset + 4])[0]
        kind = data[offset + 4:offset + 8]
        chunk = data[offset + 8:offset + 8 + size]
        if kind == b'IHDR':
            width, height, depth, color, _, _, interlace = struct.unpack('>IIBBBBB', chunk)
        elif kind == b'IDAT':
            compressed.extend(chunk)
        offset += size + 12
    if depth != 8 or color not in (2, 6) or interlace != 0:
        raise ValueError('Unsupported screenshot PNG encoding')
    channels = 3 if color == 2 else 4
    stride = width * channels
    raw = zlib.decompress(compressed)
    previous = bytearray(stride)
    bright = sampled = 0
    luminance = []
    for y in range(height):
        start = y * (stride + 1)
        mode = raw[start]
        row = bytearray(raw[start + 1:start + 1 + stride])
        for i in range(stride):
            left = row[i - channels] if i >= channels else 0
            up = previous[i]
            upper_left = previous[i - channels] if i >= channels else 0
            if mode == 1:
                predictor = left
            elif mode == 2:
                predictor = up
            elif mode == 3:
                predictor = (left + up) // 2
            elif mode == 4:
                p = left + up - upper_left
                distances = (abs(p - left), abs(p - up), abs(p - upper_left))
                predictor = (left, up, upper_left)[distances.index(min(distances))]
            elif mode == 0:
                predictor = 0
            else:
                raise ValueError('Invalid PNG row filter')
            row[i] = (row[i] + predictor) & 255
        if height * .18 <= y < height * .58 and y % 4 == 0:
            for x in range(int(width * .2), int(width * .8), 4):
                rgb = row[x * channels:x * channels + 3]
                sampled += 1
                bright += max(rgb) > 18
                luminance.append(sum(rgb) / 3)
        previous = row
    ratio = bright / max(1, sampled)
    print(f'{path}: viewport non-black pixels {ratio:.1%}')
    if statistics.pstdev(luminance) < 3:
        raise ValueError('Uniform viewport: a flat background does not establish rendered geometry')
    if ratio < .10:
        raise ValueError('Black viewport: editor chrome does not establish renderer output')


if __name__ == '__main__':
    try:
        for screenshot in sys.argv[1:]:
            check(screenshot)
    except (ValueError, OSError, zlib.error) as error:
        sys.exit(str(error))
