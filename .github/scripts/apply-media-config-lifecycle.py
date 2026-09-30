#!/usr/bin/env python3
"""Keep Later's active media/editor session alive across display rotation."""
from pathlib import Path
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply-media-config-lifecycle.py SOURCE_ROOT")

root = Path(sys.argv[1])
manifest = root / "app/src/main/AndroidManifest.xml"
text = manifest.read_text()

activity_pattern = re.compile(
    r'(<activity\b(?=[^>]*\bandroid:name\s*=\s*["\'](?:\.MainActivity|com\.night\.later\.MainActivity)["\'])[^>]*)(>)',
    re.DOTALL,
)
matches = list(activity_pattern.finditer(text))
if len(matches) != 1:
    raise SystemExit(f"{manifest}: expected one MainActivity declaration, found {len(matches)}")

required = [
    "orientation",
    "screenSize",
    "screenLayout",
    "smallestScreenSize",
    "keyboardHidden",
]

match = matches[0]
tag = match.group(1)
config_pattern = re.compile(r'\bandroid:configChanges\s*=\s*["\']([^"\']*)["\']')
config_match = config_pattern.search(tag)

if config_match:
    values = [value for value in config_match.group(1).split("|") if value]
    for value in required:
        if value not in values:
            values.append(value)
    replacement = 'android:configChanges="' + "|".join(values) + '"'
    tag = tag[:config_match.start()] + replacement + tag[config_match.end():]
else:
    # Put the lifecycle contract on the activity declaration itself. Handling
    # orientation/display-size changes in-place is deliberate for Later's
    # fullscreen image/video surfaces: it preserves the active draft and media
    # player while Compose still receives the new Configuration and relayouts.
    tag = tag.rstrip() + '\n            android:configChanges="' + "|".join(required) + '"'

updated = text[:match.start(1)] + tag + text[match.end(1):]
manifest.write_text(updated)

check = manifest.read_text()
verify_match = activity_pattern.search(check)
if verify_match is None:
    raise SystemExit(f"{manifest}: MainActivity declaration disappeared after update")
verify_config = config_pattern.search(verify_match.group(1))
if verify_config is None:
    raise SystemExit(f"{manifest}: MainActivity configChanges was not written")
actual = set(filter(None, verify_config.group(1).split("|")))
missing = [value for value in required if value not in actual]
if missing:
    raise SystemExit(f"{manifest}: missing configChanges values: {', '.join(missing)}")
