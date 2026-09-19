#!/usr/bin/env python3
"""Patch the pinned LSPosed ET snapshot to the exact libxposed API-100 contract used by Night Core."""
from __future__ import annotations

import sys
from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, got {count}")
    return text.replace(old, new, 1)


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch-lsposed-api100.py /path/to/LSPosed-ET")

    repo = Path(sys.argv[1]).resolve()
    source = repo / "daemon/src/main/java/org/lsposed/lspd/service/LSPModuleService.java"
    if not source.is_file():
        raise SystemExit(f"Missing LSPosed ET service source: {source}")

    text = source.read_text(encoding="utf-8")
    text = replace_once(
        text,
        "public ParcelFileDescriptor openRemoteFile(String path, int mode) throws RemoteException {",
        "public ParcelFileDescriptor openRemoteFile(String path) throws RemoteException {",
        "openRemoteFile signature",
    )
    text = replace_once(
        text,
        "return ParcelFileDescriptor.open(dir.resolve(path).toFile(), mode);",
        "return ParcelFileDescriptor.open(\n"
        "                    dir.resolve(path).toFile(),\n"
        "                    ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE);",
        "openRemoteFile mode",
    )
    text = replace_once(
        text,
        "    @Override\n    public Bundle featuredMethod(String name, Bundle args) {\n"
        "        return new Bundle();\n"
        "    }\n\n",
        "",
        "API-100 unsupported featuredMethod",
    )

    source.write_text(text, encoding="utf-8")
    print("LSPosed ET service patched to exact libxposed API-100 contract.")


if __name__ == "__main__":
    main()
