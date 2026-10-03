#!/usr/bin/env python3
"""Verify Android 16 KB page-size readiness for an ARM64 APK."""

from __future__ import annotations

import pathlib
import struct
import sys
import zipfile

PAGE_SIZE = 16 * 1024
PT_LOAD = 1


def elf_load_alignments(data: bytes, name: str) -> list[int]:
    if len(data) < 64 or data[:4] != b"\x7fELF":
        raise ValueError(f"{name}: not an ELF file")
    if data[4] != 2:
        raise ValueError(f"{name}: expected 64-bit ELF for arm64-v8a")
    if data[5] != 1:
        raise ValueError(f"{name}: expected little-endian ELF")

    e_phoff = struct.unpack_from("<Q", data, 32)[0]
    e_phentsize = struct.unpack_from("<H", data, 54)[0]
    e_phnum = struct.unpack_from("<H", data, 56)[0]
    if e_phentsize < 56 or e_phnum == 0:
        raise ValueError(f"{name}: missing ELF program headers")

    alignments: list[int] = []
    for index in range(e_phnum):
        offset = e_phoff + index * e_phentsize
        if offset + 56 > len(data):
            raise ValueError(f"{name}: truncated ELF program headers")
        p_type = struct.unpack_from("<I", data, offset)[0]
        if p_type == PT_LOAD:
            p_align = struct.unpack_from("<Q", data, offset + 48)[0]
            alignments.append(p_align)
    if not alignments:
        raise ValueError(f"{name}: no PT_LOAD segments")
    return alignments


def zip_data_offset(raw: bytes, info: zipfile.ZipInfo) -> int:
    offset = info.header_offset
    if raw[offset : offset + 4] != b"PK\x03\x04":
        raise ValueError(f"{info.filename}: invalid local ZIP header")
    filename_length, extra_length = struct.unpack_from("<HH", raw, offset + 26)
    return offset + 30 + filename_length + extra_length


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: verify_android_16k_page_size.py <arm64-apk>", file=sys.stderr)
        return 2

    apk = pathlib.Path(sys.argv[1])
    raw = apk.read_bytes()
    failures: list[str] = []

    with zipfile.ZipFile(apk) as archive:
        libraries = [
            info
            for info in archive.infolist()
            if (info.filename.startswith("lib/arm64-v8a/") or info.filename.startswith("lib/x86_64/")) and info.filename.endswith(".so")
        ]
        if not libraries:
            failures.append("APK contains no arm64-v8a native libraries")

        for info in libraries:
            data_offset = zip_data_offset(raw, info)
            if info.compress_type != zipfile.ZIP_STORED:
                failures.append(f"{info.filename}: native library is compressed")
            if data_offset % PAGE_SIZE != 0:
                failures.append(
                    f"{info.filename}: APK data offset {data_offset} is not 16 KB aligned"
                )

            alignments = elf_load_alignments(archive.read(info), info.filename)
            bad = [
                value
                for value in alignments
                if value < PAGE_SIZE or value % PAGE_SIZE != 0
            ]
            if bad:
                rendered = ", ".join(hex(value) for value in bad)
                failures.append(
                    f"{info.filename}: PT_LOAD alignment below/incompatible with 16 KB: {rendered}"
                )
            print(
                f"OK {info.filename}: zip_offset={data_offset} "
                f"load_align={','.join(hex(value) for value in alignments)}"
            )

    if failures:
        for failure in failures:
            print(f"FAIL {failure}", file=sys.stderr)
        return 1

    print(f"PASS {apk}: Android 16 KB page-size checks passed")
    return 0


if __name__ == "__main__":
    paths = sys.argv[1:]
    for path in paths:
        sys.argv = [sys.argv[0], path]
        result = main()
        if result:
            raise SystemExit(result)
