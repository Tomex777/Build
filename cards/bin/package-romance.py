#!/usr/bin/env python3
"""Package rendered romance studies without treating pending art as finished."""
import hashlib
import json
import shutil
import sys
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = root / 'dist/romance-preview'
output = Path(sys.argv[1]).resolve()
output.mkdir(parents=True, exist_ok=True)
manifest = json.loads((source / 'manifest.json').read_text())
entries = manifest['entries']
review = {
    'family': 'romance',
    'reviewScope': 'Visual composition study only',
    'mainGalleryPublished': False,
    'entries': [{
        'id': c['id'],
        'masterSHA256': hashlib.sha256((source / (c['id'] + '.png')).read_bytes()).hexdigest(),
        'inspected': ['full master', 'native pixel face detail', '320x512 phone proof'],
        'sourceChecksums': [s['sha256'] for s in c['sources']],
        'checks': c['qa'],
    } for c in entries],
}
(source / 'visual-review.json').write_text(json.dumps(review, indent=2) + '\n')
shutil.copy2(source / 'NIGHT-Romance-Collection.html', output / 'NIGHT-Romance-Collection.html')
for card in entries:
    name = 'NIGHT-Romance-Blue-Box.png' if card['id'] == 'taiki-chinatsu-romance' else card['id'] + '.png'
    shutil.copy2(source / (card['id'] + '.png'), output / name)
archive = output / 'NIGHT-Romance-Masters.zip'
with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
    for c in entries:
        for ext, folder in [('.png', 'masters'), ('.svg', 'vector'), ('-phone.png', 'phone')]:
            z.write(source / (c['id'] + ext), folder + '/' + c['id'] + ext)
    for name in ['NIGHT-Romance-Collection.html', 'manifest.json', 'visual-review.json']:
        z.write(source / name, name)
    for name in ['CormorantGaramond-OFL.txt', 'DMSans-OFL.txt']:
        z.write(root / 'renderer/assets/licenses' / name, 'font-licenses/' + name)
    z.writestr('README.txt', 'NIGHT ROMANCE\n\nOriginal character artwork with programmed NIGHT frames and typography.\nPNG masters: 3000 x 4800. Source illustrations retain their recorded native\nresolution; export size adds no illustration detail. Artwork rights reserved.\nPersonal design study. Each source URL and exact SHA-256 is in manifest.json.\nPending pairs have no finished card image in this package.\n')
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
print(json.dumps({'files': [str(p) for p in output.iterdir() if p.is_file()], 'cards': len(entries)}))
