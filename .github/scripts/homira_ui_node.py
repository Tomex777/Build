import re
import sys
import xml.etree.ElementTree as ET

mode, target = sys.argv[1], sys.argv[2]
root = ET.parse("/tmp/homira-window.xml").getroot()
matches = []

for node in root.iter("node"):
    text = node.attrib.get("text", "")
    desc = node.attrib.get("content-desc", "")
    if text == target or desc == target:
        bounds = node.attrib.get("bounds", "")
        found = re.fullmatch(
            r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",
            bounds,
        )
        if found:
            left, top, right, bottom = map(int, found.groups())
            matches.append(((left + right) // 2, (top + bottom) // 2))

if not matches:
    raise SystemExit(f"UI node not found: {target!r}")

if mode == "assert":
    raise SystemExit(0)

if mode == "tap":
    print(*matches[0])
    raise SystemExit(0)

raise SystemExit(f"Unsupported mode: {mode!r}")
