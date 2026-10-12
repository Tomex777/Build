# MSCC HD personality artwork

Menu and group-introduction art uses **full-resolution user-provided images**
from a persistent folder, not the obsolete 128×160 thumbnails stored in Git.

The image upload ZIP is available from the conversation as
`MSCC_High_Resolution_Personality_Art.zip`; it contains nine original JPEG
files at **1024×1536 or 1229×1536**, not upscaled previews.

## Image assignments

- Josia (josiah): moonlit black-robed scholar reading in the library.
- Nami: beach/manga reader with the visible `NAMI` label.
- MiMi: seven red-haired illustrations — scholar, swordswoman,
  northern navigator, celebration, Egyptian scroll, Mediterranean scholar,
  and Viking toast.

All nine are placed in each personality's `menu/` collection. Until
dedicated HD group-intro art is added later, intro messages reuse the
personality's HD menu art. Images rotate within each collection.

## Upload once to Azure

Copy the ZIP from your computer/phone to the VM, e.g. with SCP:

```bash
scp MSCC_High_Resolution_Personality_Art.zip azureuser@<AZURE_IP>:/tmp/
```

Then run as `azureuser` on the VM (with updated MSCC code):

```bash
bash /opt/mscc/current/tools/install-personality-art.sh \
  /tmp/MSCC_High_Resolution_Personality_Art.zip
```

The importer validates every filename, verifies each image is genuinely
at least 850 px on the short side and 1200 px on the long side, decodes it,
and then atomically installs each file into `/var/lib/mscc/personality-art`.
It never touches WhatsApp auth or private configuration.

The selection logic reads this persistent folder first. It survives
`/opt/mscc/current` release switches and requires no restart.

## Adding art later

Put new JPEG, PNG, or WebP files under:
`/var/lib/mscc/personality-art/<josiah|nami|mimi>/menu/`.

Optional dedicated introductions:

- `.../<profile>/intro/first/<name>.jpg`
- `.../<profile>/intro/return/<name>.jpg`

If missing, the menu images are also used in group introductions. Old
bundled images remain a fallback only if an HD directory is empty.
