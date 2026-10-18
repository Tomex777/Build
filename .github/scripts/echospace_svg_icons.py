#!/usr/bin/env python3
"""Convert authored EchoSpace SVG symbols to Android VectorDrawable and use them in Compose.

Canonical authoring artwork lives in EchoSpace/icons/echospace-icons.svg.
The same path geometry is converted to runtime assets: no bitmaps, emojis or
generic Material icons are substituted for custom sound and studio controls.
"""
from pathlib import Path
from xml.etree import ElementTree as ET
from xml.sax.saxutils import escape
import re

root = Path("EchoSpace")
source = root / "icons/echospace-icons.svg"
res = root / "app/src/main/res/drawable"
kt = root / "app/src/main/java/com/nexa/echospace/MainActivity.kt"
doc = ET.parse(source).getroot()
ns = {"s": "http://www.w3.org/2000/svg"}
symbols = doc.findall(".//s:symbol", ns)
required_sounds = {
    "rain", "thunder", "ocean", "wind", "fire", "chimes", "birds",
    "night", "stream", "waterfall", "leaves", "owl", "frogs", "traffic",
    "drizzle", "sleet", "cafe", "train", "fan", "white_noise",
    "pink_noise", "brown_noise"
}
required_controls = {
    "mixer", "add", "save", "close", "speaker", "mute", "remove",
    "rotate_left", "rotate_right", "play", "pause", "timer", "waveform"
}
names = [node.attrib.get("id") for node in symbols]
assert len(names) == len(set(names)), "Duplicate SVG source identifiers"
assert all(re.fullmatch("[a-z][a-z_]*", name or "") for name in names)
assert required_sounds | required_controls <= set(names), (
    (required_sounds | required_controls) - set(names)
)
res.mkdir(parents=True, exist_ok=True)

for symbol in symbols:
    name = symbol.attrib["id"]
    paths = symbol.findall("s:path", ns)
    assert paths, "Empty SVG icon " + name
    # Plain SVG path commands are supported directly by Android's pathData.
    xml_paths = []
    for shape in paths:
        d = shape.attrib["d"]
        assert d and not re.search(r"[\x00-\x1f]", d), name
        xml_paths.append(
            '    <path android:pathData="' + escape(d, {'"': '&quot;'}) +
            '" android:fillColor="#00000000" android:strokeColor="#FFFFFFFF"'
            ' android:strokeWidth="1.8" android:strokeLineCap="round"'
            ' android:strokeLineJoin="round"/>\n'
        )
    drawable = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"'
        ' android:width="24dp" android:height="24dp"'
        ' android:viewportWidth="24" android:viewportHeight="24">\n'
        + "".join(xml_paths) + "</vector>\n"
    )
    ET.fromstring(drawable)
    (res / ("es_" + name + ".xml")).write_text(drawable)

s = kt.read_text()
imports = "import androidx.compose.ui.res.painterResource\n"
assert imports not in s
assert "import androidx.compose.material3.*\n" in s
s = s.replace("import androidx.compose.material3.*\n",
              "import androidx.compose.material3.*\n" + imports, 1)

# Replace free-form title heuristics with exhaustive compile-time enum mapping.
match = re.search(
    r'private fun soundGlyph\(kind: SoundKind\): ImageVector\s*\{.*?\}\s*'
    r'(?=@Composable\s*\nprivate fun ExpandedSoundMixer)',
    s, flags=re.S
)
if not match:
    raise RuntimeError("Expected stock Material soundGlyph implementation")
cases = "\n".join(
    "        SoundKind." + kind.upper() + " -> R.drawable.es_" + kind
    for kind in sorted(required_sounds)
)
replacement = (
    "private fun soundGlyph(kind: SoundKind): Int = when (kind) {\n"
    + cases + "\n    }\n\n"
)
s = s[:match.start()] + replacement + s[match.end():]

# All uses of sound glyphs must now load the authored vectors, preserving tint.
s = re.sub(
    r'Icon\(soundGlyph\((sound\.kind|kind)\),',
    r'Icon(painterResource(soundGlyph(\1)),',
    s
)
assert "Icon(soundGlyph(" not in s

actions = {
    "Tune": "mixer",
    "Add": "add",
    "BookmarkAdd": "save",
    "Close": "close",
    "VolumeUp": "speaker",
    "VolumeOff": "mute",
    "DeleteOutline": "remove",
    "RotateLeft": "rotate_left",
    "RotateRight": "rotate_right",
    "PlayArrow": "play",
    "Pause": "pause",
    "Timer": "timer",
    "GraphicEq": "waveform",
}
for stock, name in actions.items():
    s = s.replace("Icons.Default." + stock,
                  "painterResource(R.drawable.es_" + name + ")")
# Don't mark it clean if any Material drawable is still used in the app icon code.
remaining = sorted(set(re.findall(r"Icons\.Default\.[A-Za-z0-9_]+", s)))
if remaining:
    print("REMAINING_MATERIAL_ICONS", remaining, flush=True)

assert "Slider(" not in s and "SliderDefaults" not in s, "ABR stock slider reappeared"
assert ".emoji" not in s, "Sound icons must never use emoji"
kt.write_text(s)
print("ECHOSPACE_AUTHORED_SVG_LOADED", len(symbols), "icons",
      len(required_sounds), "unique sound symbols",
      "remaining_stock", remaining, flush=True)
