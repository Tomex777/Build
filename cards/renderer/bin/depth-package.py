"""Package complete reusable source and compact rendered evidence, not giant masters."""
from pathlib import Path
import hashlib
import os
import shutil
import tempfile
import zipfile

root = Path(__file__).resolve().parents[1]
destination = root.parent / 'NIGHT-Depth-Source.zip'
files = set()
for directory in ('src', 'bin', 'tests', 'examples', 'assets', 'docs'):
    files.update(p for p in (root / directory).rglob('*') if p.is_file())
files.update(p for p in root.iterdir() if p.is_file() and p.suffix in ('.md', '.txt', '.json'))
files.add(root / 'LICENSE')
files.add(root / 'dist/NIGHT-Depth-Editions.html')
for directory in ('dist/depth', 'dist/naruto'):
    files.update(p for p in (root / directory).glob('*') if p.suffix in ('.svg', '.html', '.json', '.txt'))
for directory in ('qa/depth-01', 'qa/depth-02', 'qa/depth-03'):
    p = root / directory / 'comparison.png'
    if p.exists(): files.add(p)
for p in (root / 'qa/depth-final').glob('*'):
    if p.is_file() and p.name != 'browser-export.png' and p.suffix in ('.png', '.json'):
        files.add(p)

with tempfile.TemporaryDirectory(prefix='night-source-') as temporary:
    encoded = Path(temporary) / destination.name
    manifest = []
    with zipfile.ZipFile(encoded, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for p in sorted(files):
            relative = p.relative_to(root).as_posix()
            archive.write(p, 'NIGHT-Depth-Editions/' + relative)
            manifest.append(hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + relative)
        archive.writestr('NIGHT-Depth-Editions/SOURCE-MANIFEST.sha256', '\n'.join(manifest) + '\n')
    with zipfile.ZipFile(encoded) as archive:
        assert archive.testzip() is None, 'ZIP CRC verification failed'
    pending = destination.with_suffix('.zip.pending')
    shutil.copyfile(encoded, pending)
    with pending.open('r+b') as f: os.fsync(f.fileno())
    os.replace(pending, destination)
print(f'{destination}: {len(files)} files, {destination.stat().st_size} bytes; ZIP CRC verified')
