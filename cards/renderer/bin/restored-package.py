"""Package the restored renders and complete reusable source without old ZIPs."""
from pathlib import Path
import json, shutil, zipfile, hashlib, sys

root = Path(__file__).resolve().parents[1]
workspace = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else root.parents[1]
out = root / 'dist/restored'
files = {
    'NIGHT-Restored-Pair.png': out / 'NIGHT-Restored-Pair.png',
    'NIGHT-Nocturne-No-Circle-4K.png': out / 'ichigo-nocturne-restored.png',
    'NIGHT-Tidelight-Restored-4K.png': out / 'ichigo-tidelight-restored.png',
    'NIGHT-Nocturne-No-Circle.svg': out / 'ichigo-nocturne-restored.svg',
    'NIGHT-Tidelight-Restored.svg': out / 'ichigo-tidelight-restored.svg',
    'NIGHT-Ichigo-Collection-Proposal.json': root / 'examples/ichigo-collection-proposal.json',
}
verification = json.loads((root / 'qa/restored-final/verification.json').read_text())
assert len(verification['checks']) == 8
assert all(c['exactRequestedChange'] for c in verification['checks'])
workspace.mkdir(parents=True, exist_ok=True)
for name, source in files.items():
    shutil.copy2(source, workspace / name)
archive = workspace / 'NIGHT-Restored-Source.zip'
pending = archive.with_suffix('.zip.pending')
include = []
for directory in ['src', 'bin', 'examples', 'assets', 'tests', 'docs', 'preserved', 'qa/restored-final']:
    include.extend(p for p in (root / directory).rglob('*') if p.is_file() and '__pycache__' not in p.parts)
include.extend(p for p in root.iterdir() if p.is_file() and p.suffix in ['.md', '.json'])
include.extend(p for p in out.iterdir() if p.is_file())
include.append(root / 'qa/noctis-preserved-baseline.json')
if (root / 'LICENSE').exists():
    include.append(root / 'LICENSE')
with zipfile.ZipFile(pending, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=4) as z:
    for source in sorted(set(include)):
        z.write(source, Path('NIGHT-Restored') / source.relative_to(root))
with zipfile.ZipFile(pending) as z:
    bad = z.testzip()
    assert bad is None, bad
    count = len(z.infolist())
pending.replace(archive)
manifest = {
    'files': [{'file': name, 'bytes': (workspace / name).stat().st_size,
               'sha256': hashlib.sha256((workspace / name).read_bytes()).hexdigest()}
              for name in list(files) + [archive.name]],
    'archiveEntries': count, 'crcValid': True, 'renderDimensions': [3000, 4800],
    'originalFilesUnchanged': verification['preservedFiles'],
    'catalogueStatus': 'proposal-only', 'formsRendered': ['original-shikai'],
}
(workspace / 'NIGHT-Restored-Manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
print('Packaged', count, 'files;', archive.stat().st_size, 'bytes; CRC valid.')
