# Night's seven missing media commands

These are **new**, distinct commands. Existing partial features `.compress`, `.crop`, and `.trim` are intentionally untouched.

| Command | Input | Output | Limit |
| --- | --- | --- | --- |
| `.removebg` | Reply to image | Transparent PNG document | 12 MB image, max 1024 px processing, local rembg/u2netp |
| `.bestframe [1-5]` | Reply to video | 1–5 JPEG images ordered by timeline | First 24 seconds, 320 px scoring frames |
| `.reverse` | Reply to video/audio | MP4 or MP3 | Video first 14 seconds or audio first 40 seconds |
| `.stabilize` | Reply to video | MP4 | First 24 seconds; FFmpeg vidstab if available, deshake fallback |
| `.emojimix 😍 🔥` | Exactly two emojis | WhatsApp sticker | Twemoji artwork, joined locally into a collage |
| `.ssweb https://github.com` | Public website | JPEG screenshot | One viewport, opt-in allowlist, no JS by default |
| `.getexif` | Reply to image or photo document | Selected camera/EXIF fields | GPS and unique identifiers excluded |

## Existing commands protected

This change does **not** add `.compress`, `.crop` or `.trim`, alter existing sticker corner geometry, overwrite existing image-upscale behavior, or change routing/pairing. CPU-heavy extra media conversions share a strict single active job slot to limit effects on `.ping` and normal WhatsApp use.

## Installation

The usual Night deployment already supplies FFmpeg and the existing JavaScript canvas renderer.

For the optional native dependencies on the Azure VM:

```sh
bash /opt/mscc/current/tools/install-extra-media-tools.sh
```

This installs EXIFTool, a private `rembg` Python CPU virtual environment, and an **isolated** Puppeteer-core browser driver plus Chrome for Testing in `/opt/mscc/tools`. The screenshot driver does not change Night's main `package.json` dependencies, allowing production upgrades to reuse the previously installed core modules. They are **not** installed automatically on all deployments because their downloads and disk usage can be substantial. Confirm disk space and resources first. First use of `.removebg` may download the small u2netp model.

To install just one extra provider: `MSCC_INSTALL_REMBG=0` or `MSCC_INSTALL_BROWSER=0` when running the script. The installer does not restart Night, change WhatsApp sessions, or write private settings.

For `.ssweb`, the operator must explicitly set a trusted public-site allowlist in `/etc/mscc.env`. Example:

```
SSWEB_ALLOWED_HOSTS=github.com,wikipedia.org,example.com
SSWEB_CHROME_PATH=/opt/mscc/tools/chromium
SSWEB_ENABLE_JS=0
SSWEB_NO_SANDBOX=0
```

Only specified domains and their subdomains are valid capture targets. DNS resolution rejects local, private, metadata, IPv6, and reserved IPs; secondary network requests also pass through validation. Main-frame redirects are pinned to the allowed target's hostname. Chrom(ium) should run as a restricted user. The browser sandbox is on unless explicitly disabled. Never add internal hostnames or private services to the allowlist.

Emoji artwork comes from **Twemoji**, licensed under CC BY 4.0. This command creates an overlapping collage of two emojis; it does not claim to reproduce Google's proprietary Emoji Kitchen combinations.

EXIFTool may not return camera fields for WhatsApp-compressed images: reply to an original photo **document** if preserving camera metadata matters. GPS, serial numbers, and device identifiers are never returned by the command.

## Tests

```sh
cd mscc && npm run check:extra-media
```

This runs command registry checks, URL/SSRF validation, EXIF redaction checks, local emoji sticker rendering, and FFmpeg frame extraction/reversal/stabilization. Real `.removebg`, `.ssweb` and WhatsApp end-to-end operation require installing their tools and testing on the linked server.

Normal deployment remains controlled by the existing `mscc-azure` workflows and does **not** occur until this pull request is merged.
