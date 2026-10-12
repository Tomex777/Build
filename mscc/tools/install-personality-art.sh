#!/usr/bin/env bash
set -euo pipefail

# Import user-provided full-resolution art into persistent Azure state.
# Usage: bash /opt/mscc/current/tools/install-personality-art.sh /tmp/mscc-personality-art-hd.zip
ARCHIVE="${1:-}"
if [[ ! -f "$ARCHIVE" ]]; then
  echo "Usage: $0 /path/to/personality-art.zip" >&2
  exit 2
fi
command -v ffprobe >/dev/null || { echo "ffprobe (ffmpeg package) is required." >&2; exit 2; }

python3 - "$ARCHIVE" <<'PY'
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
import tempfile
from zipfile import ZipFile

archive = Path(sys.argv[1])
root = Path(os.environ.get('MSCC_PERSONALITY_ART_DIR', '/var/lib/mscc/personality-art'))
allowed = re.compile(r'^(josiah|nami|mimi)/(menu|intro/(?:first|return))/[a-zA-Z0-9_-]+\.(?:webp|png|jpe?g)$')
max_files = 60
max_file = 12 * 1024 * 1024
max_total = 100 * 1024 * 1024

# Only profile menu/intro images can be extracted; prohibit traversal and
# symlinks. Never replace other MSCC settings, DB or WhatsApp credentials.
with ZipFile(archive) as zf:
    entries = [i for i in zf.infolist() if not i.is_dir() and i.filename != 'MANIFEST.json' and i.filename != 'README.txt']
    if not entries or len(entries) > max_files:
        raise SystemExit('Invalid artwork count (1-60 required).')
    if sum(i.file_size for i in entries) > max_total:
        raise SystemExit('Artwork archive exceeds the 100 MB limit.')
    names = set()
    for item in entries:
        name = item.filename
        if not allowed.fullmatch(name) or name in names:
            raise SystemExit('Unexpected or duplicate artwork path; expected <profile>/menu/<name>.webp (or intro/first, intro/return).')
        if item.file_size <= 1024 or item.file_size > max_file:
            raise SystemExit('Artwork file is empty or exceeds 12 MB.')
        if ((item.external_attr >> 16) & 0o170000) == 0o120000:
            raise SystemExit('Symlink entries are not allowed.')
        names.add(name)

    root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.hd-art-stage-', dir=root) as stage:
        stage = Path(stage)
        for item in entries:
            path = stage / item.filename
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(zf.read(item))
            try:
                info = subprocess.run(
                    ['ffprobe', '-v', 'error', '-select_streams', 'v:0',
                     '-show_entries', 'stream=width,height',
                     '-of', 'json', str(path)],
                    check=True, capture_output=True, text=True, timeout=20,
                )
                size = json.loads(info.stdout)['streams'][0]
                width, height = int(size['width']), int(size['height'])
            except (subprocess.SubprocessError, ValueError, KeyError, IndexError) as exc:
                raise SystemExit(f'Unreadable image {item.filename}: {type(exc).__name__}') from exc
            if min(width, height) < 850 or max(width, height) < 1200 or max(width, height) > 4096:
                raise SystemExit(f'Low-resolution image rejected: {item.filename} ({width}x{height}); minimum 850x1200.')
            # Fully decode first frame to catch truncated images before install.
            try:
                subprocess.run(
                    ['ffmpeg', '-v', 'error', '-i', str(path),
                     '-frames:v', '1', '-f', 'null', '-'],
                    check=True, capture_output=True, timeout=25,
                )
            except (subprocess.SubprocessError, ValueError) as exc:
                raise SystemExit(f'Corrupt image rejected: {item.filename}') from exc

        # All files passed the quality gate; move atomically one file at a time.
        for item in entries:
            destination = root / item.filename
            destination.parent.mkdir(parents=True, exist_ok=True)
            os.chmod(stage / item.filename, 0o644)
            os.replace(stage / item.filename, destination)
            print(f'Installed HD artwork: {item.filename}')
print(f'Installed {len(entries)} high-resolution personality images into persistent MSCC state.')
print('No service restart required. New artwork is selected on the next menu or intro.')
PY
