#!/usr/bin/env python3
"""Hoist Later media overlays out of LazyColumn items.

Fullscreen viewers/editors must not be owned by MediaAttachmentBlock because a
lazy item can leave composition when rotation reduces the viewport. The screen
owns the selected block + overlay mode; the lazy item only renders the card.
"""

from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply-media-overlay-hoist.py SOURCE_ROOT")

root = Path(sys.argv[1])
path = root / "app/src/main/java/com/night/later/ui/editor/CapsuleEditorScreen.kt"
source = path.read_text()


def replace_once(old: str, new: str, label: str) -> None:
    global source
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one {label}, found {count}")
    source = source.replace(old, new)


state_anchor = """    var showFormatSheet by remember {
        mutableStateOf(false)
    }
"""
state_insert = state_anchor + """
    // Fullscreen media surfaces live at screen scope, not inside a LazyColumn item.
    // A media block can leave composition when rotation shrinks the viewport; keeping
    // the modal state here prevents the active viewer/player from disappearing.
    var mediaOverlayBlockId by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf<String?>(null)
    }

    var mediaOverlayMode by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf<String?>(null)
    }

    var mediaOverlayViewingOriginal by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(false)
    }
"""
replace_once(state_anchor, state_insert, "screen overlay state anchor")

old_call = """                                    MediaAttachmentBlock(
                                        block = block,
                                        canMoveUp = index > 0,
                                        canMoveDown = index < blocks.lastIndex - 1,
                                        onMoveUp = { moveBlock(block.id, -1) },
                                        onMoveDown = { moveBlock(block.id, 1) },
                                        onImageEdited = { edited ->
                                            markMeaningfulEdit(beforeBlockId = block.id)
                                            blocks = blocks.map { item ->
                                                if (item.id == block.id) {
                                                    item.copy(
                                                        sourceUri = edited.uri,
                                                        sourcePath = edited.path,
                                                        mimeType = edited.mimeType,
                                                        displayName = edited.displayName,
                                                        originalMedia = item.originalMedia ?: CapsuleMediaVersion(
                                                            sourceUri = item.sourceUri,
                                                            sourcePath = item.sourcePath,
                                                            mimeType = item.mimeType,
                                                            displayName = item.displayName
                                                        )
                                                    )
                                                } else {
                                                    item
                                                }
                                            }
                                        },
                                        onVideoEdited = { edited ->
                                            markMeaningfulEdit(beforeBlockId = block.id)
                                            blocks = blocks.map { item ->
                                                if (item.id == block.id) {
                                                    item.copy(
                                                        sourceUri = edited.uri,
                                                        sourcePath = edited.path,
                                                        mimeType = edited.mimeType,
                                                        displayName = edited.displayName,
                                                        originalMedia = item.originalMedia ?: CapsuleMediaVersion(
                                                            sourceUri = item.sourceUri,
                                                            sourcePath = item.sourcePath,
                                                            mimeType = item.mimeType,
                                                            displayName = item.displayName
                                                        )
                                                    )
                                                } else item
                                            }
                                        },
                                        onRemove = {
                                            markWritingActivity()
                                            block.sourcePath?.let { File(it).delete() }
                                            blocks = blocks.filterNot { it.id == block.id }
                                        }
                                    )
"""
new_call = """                                    MediaAttachmentBlock(
                                        block = block,
                                        canMoveUp = index > 0,
                                        canMoveDown = index < blocks.lastIndex - 1,
                                        onMoveUp = { moveBlock(block.id, -1) },
                                        onMoveDown = { moveBlock(block.id, 1) },
                                        onOpen = {
                                            mediaOverlayBlockId = block.id
                                            mediaOverlayMode =
                                                if (block.type == CapsuleBlockType.VIDEO) {
                                                    "video-viewer"
                                                } else {
                                                    "image-viewer"
                                                }
                                            mediaOverlayViewingOriginal = false
                                        },
                                        onRemove = {
                                            markWritingActivity()
                                            block.sourcePath?.let { File(it).delete() }
                                            blocks = blocks.filterNot { it.id == block.id }
                                        }
                                    )
"""
replace_once(old_call, new_call, "media attachment invocation")

host_anchor = """    if (showMoodSheet) {
"""
host = """    val mediaOverlayBlock = mediaOverlayBlockId?.let { selectedId ->
        blocks.firstOrNull { it.id == selectedId }
    }
    if (mediaOverlayBlock != null) {
        val selectedBlock = mediaOverlayBlock
        val viewingOriginal =
            mediaOverlayViewingOriginal && selectedBlock.originalMedia != null
        val imagePath = if (viewingOriginal) {
            selectedBlock.originalMedia?.sourcePath ?: selectedBlock.sourcePath
        } else {
            selectedBlock.sourcePath
        }
        val mediaSource = if (selectedBlock.type == CapsuleBlockType.VIDEO) {
            if (viewingOriginal) {
                selectedBlock.originalMedia?.sourceUri
                    ?: selectedBlock.originalMedia?.sourcePath
                    ?: selectedBlock.sourceUri
                    ?: selectedBlock.sourcePath
            } else {
                selectedBlock.sourceUri ?: selectedBlock.sourcePath
            }
        } else {
            imagePath
        }
        val mediaMimeType = if (viewingOriginal) {
            selectedBlock.originalMedia?.mimeType ?: selectedBlock.mimeType
        } else {
            selectedBlock.mimeType
        }
        val mediaDisplayName = if (viewingOriginal) {
            selectedBlock.originalMedia?.displayName ?: selectedBlock.displayName
        } else {
            selectedBlock.displayName
        }
        val versionActionLabel = if (selectedBlock.originalMedia != null) {
            if (viewingOriginal) "Edited" else "Original"
        } else {
            null
        }
        val switchVersion: (() -> Unit)? = if (selectedBlock.originalMedia != null) {
            { mediaOverlayViewingOriginal = !mediaOverlayViewingOriginal }
        } else {
            null
        }

        when (mediaOverlayMode) {
            "image-viewer" -> {
                if (!imagePath.isNullOrBlank()) {
                    LaterFullscreenImageViewer(
                        path = imagePath,
                        displayName = mediaDisplayName,
                        mimeType = mediaMimeType,
                        onDismiss = {
                            mediaOverlayMode = null
                            mediaOverlayBlockId = null
                            mediaOverlayViewingOriginal = false
                        },
                        versionActionLabel = versionActionLabel,
                        onSwitchVersion = switchVersion,
                        onEdit =
                            if (mediaMimeType.equals("image/gif", ignoreCase = true)) {
                                null
                            } else {
                                { mediaOverlayMode = "image-editor" }
                            },
                        onDelete = {
                            markWritingActivity()
                            selectedBlock.sourcePath?.let { File(it).delete() }
                            blocks = blocks.filterNot { it.id == selectedBlock.id }
                            mediaOverlayMode = null
                            mediaOverlayBlockId = null
                            mediaOverlayViewingOriginal = false
                        }
                    )
                }
            }

            "image-editor" -> {
                if (!imagePath.isNullOrBlank()) {
                    LaterImageEditor(
                        path = imagePath,
                        displayName = mediaDisplayName,
                        sourceMimeType = mediaMimeType,
                        onDismiss = { mediaOverlayMode = "image-viewer" },
                        onSaved = { edited ->
                            markMeaningfulEdit(beforeBlockId = selectedBlock.id)
                            blocks = blocks.map { item ->
                                if (item.id == selectedBlock.id) {
                                    item.copy(
                                        sourceUri = edited.uri,
                                        sourcePath = edited.path,
                                        mimeType = edited.mimeType,
                                        displayName = edited.displayName,
                                        originalMedia =
                                            item.originalMedia ?: CapsuleMediaVersion(
                                                sourceUri = item.sourceUri,
                                                sourcePath = item.sourcePath,
                                                mimeType = item.mimeType,
                                                displayName = item.displayName
                                            )
                                    )
                                } else {
                                    item
                                }
                            }
                            mediaOverlayMode = null
                            mediaOverlayBlockId = null
                            mediaOverlayViewingOriginal = false
                        }
                    )
                }
            }

            "video-viewer" -> {
                if (!mediaSource.isNullOrBlank()) {
                    LaterFullscreenVideoViewer(
                        source = mediaSource,
                        displayName = mediaDisplayName ?: "Video",
                        mimeType = mediaMimeType,
                        onDismiss = {
                            mediaOverlayMode = null
                            mediaOverlayBlockId = null
                            mediaOverlayViewingOriginal = false
                        },
                        onEdit = { mediaOverlayMode = "video-editor" },
                        versionActionLabel = versionActionLabel,
                        onSwitchVersion = switchVersion
                    )
                }
            }

            "video-editor" -> {
                if (!mediaSource.isNullOrBlank()) {
                    LaterVideoEditor(
                        source = mediaSource,
                        displayName = mediaDisplayName,
                        onDismiss = { mediaOverlayMode = "video-viewer" },
                        onSaved = { edited ->
                            markMeaningfulEdit(beforeBlockId = selectedBlock.id)
                            blocks = blocks.map { item ->
                                if (item.id == selectedBlock.id) {
                                    item.copy(
                                        sourceUri = edited.uri,
                                        sourcePath = edited.path,
                                        mimeType = edited.mimeType,
                                        displayName = edited.displayName,
                                        originalMedia =
                                            item.originalMedia ?: CapsuleMediaVersion(
                                                sourceUri = item.sourceUri,
                                                sourcePath = item.sourcePath,
                                                mimeType = item.mimeType,
                                                displayName = item.displayName
                                            )
                                    )
                                } else {
                                    item
                                }
                            }
                            mediaOverlayMode = null
                            mediaOverlayBlockId = null
                            mediaOverlayViewingOriginal = false
                        }
                    )
                }
            }
        }
    }

""" + host_anchor
replace_once(host_anchor, host, "screen overlay host")

start_marker = """@Composable
private fun MediaAttachmentBlock(
"""
end_marker = """
@Composable
private fun VoiceDraftBlock"""
start = source.find(start_marker)
end = source.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit(f"{path}: could not locate MediaAttachmentBlock")

old_function = source[start:end]
card_start = old_function.find("    val bitmap = rememberAttachmentThumbnail(block)")
overlay_start = old_function.find("\n    val imagePath =", card_start)
if card_start < 0 or overlay_start < 0:
    raise SystemExit(f"{path}: could not split MediaAttachmentBlock card from overlays")

card_body = old_function[card_start:overlay_start]
local_state = """    var showImageViewer by androidx.compose.runtime.saveable.rememberSaveable(block.id) {
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
if card_body.count(local_state) != 1:
    raise SystemExit(f"{path}: media item local overlay state did not match")
card_body = card_body.replace(local_state, "")

old_click = """        onClick = {
            if (mediaSource.isNullOrBlank()) return@Surface
            if (block.type == CapsuleBlockType.VIDEO) {
                showVideoViewer = true
            } else {
                showImageViewer = true
            }
        }
"""
new_click = """        onClick = {
            if (mediaSource.isNullOrBlank()) return@Surface
            onOpen()
        }
"""
if card_body.count(old_click) != 1:
    raise SystemExit(f"{path}: media card click handler did not match")
card_body = card_body.replace(old_click, new_click)

new_function = """@Composable
private fun MediaAttachmentBlock(
    block: DraftCapsuleBlock,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
""" + card_body + """
}
"""
source = source[:start] + new_function + source[end:]

if source.count("Fullscreen media surfaces live at screen scope") != 1:
    raise SystemExit(f"{path}: screen overlay ownership marker missing")
if source.count("LaterFullscreenVideoViewer(") != 1:
    raise SystemExit(f"{path}: expected one screen-owned fullscreen video viewer")
if "showVideoViewer by" in source or "showImageViewer by" in source:
    raise SystemExit(f"{path}: lazy item still owns fullscreen viewer state")
if "onImageEdited:" in source or "onVideoEdited:" in source:
    raise SystemExit(f"{path}: lazy media block still owns editor callbacks")

path.write_text(source)
