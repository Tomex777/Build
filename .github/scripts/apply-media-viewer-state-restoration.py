#!/usr/bin/env python3
"""Preserve fullscreen media viewer state across Android configuration recreation."""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply-media-viewer-state-restoration.py SOURCE_ROOT")

root = Path(sys.argv[1])
path = root / "app/src/main/java/com/night/later/ui/editor/CapsuleEditorScreen.kt"
text = path.read_text()

old = """    var showImageViewer by remember(block.id) { mutableStateOf(false) }
    var showImageEditor by remember(block.id) { mutableStateOf(false) }
    var showVideoViewer by remember(block.id) { mutableStateOf(false) }
    var showVideoEditor by remember(block.id) { mutableStateOf(false) }
    var videoEditSource by remember(block.id) { mutableStateOf<String?>(null) }
    var viewingOriginal by remember(block.id) { mutableStateOf(false) }
"""

new = """    var showImageViewer by androidx.compose.runtime.saveable.rememberSaveable(block.id) {
        mutableStateOf(false)
    }
    var showImageEditor by remember(block.id) { mutableStateOf(false) }
    var showVideoViewer by androidx.compose.runtime.saveable.rememberSaveable(block.id) {
        mutableStateOf(false)
    }
    var showVideoEditor by remember(block.id) { mutableStateOf(false) }
    var videoEditSource by remember(block.id) { mutableStateOf<String?>(null) }
    var viewingOriginal by androidx.compose.runtime.saveable.rememberSaveable(block.id) {
        mutableStateOf(false)
    }
"""

count = text.count(old)
if count != 1:
    raise SystemExit(f"{path}: expected one media viewer state block, found {count}")

path.write_text(text.replace(old, new, 1))
