#!/usr/bin/env python3
"""Enforce production-facing Later UI cleanup after feature overlays."""
from pathlib import Path
import re
import sys
if len(sys.argv) != 2:
    raise SystemExit("usage: apply-production-ui-cleanup.py SOURCE_ROOT")
root = Path(sys.argv[1])

def remove_text_call(source: str, marker: str) -> str:
    if source.count(marker) != 1:
        raise SystemExit(f"expected one production-copy anchor {marker!r}, found {source.count(marker)}")
    marker_at = source.index(marker)
    call_at = source.rfind("Text(", 0, marker_at)
    if call_at < 0:
        raise SystemExit(f"could not find Text() containing {marker!r}")
    open_at = source.find("(", call_at)
    depth = 0
    i = open_at
    in_string = False
    escaped = False
    while i < len(source):
        ch = source[i]
        if in_string:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                in_string = False
        else:
            if ch == '"':
                in_string = True
            elif ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    end = i + 1
                    line_start = source.rfind("\n", 0, call_at) + 1
                    line_end = source.find("\n", end)
                    if line_end < 0:
                        line_end = end
                    else:
                        line_end += 1
                    return source[:line_start] + source[line_end:]
        i += 1
    raise SystemExit(f"unterminated Text() containing {marker!r}")

def remove_all_text_calls(source: str, marker: str) -> tuple[str, int]:
    """Remove every Compose Text(...) call that contains the exact marker."""
    removed = 0
    while marker in source:
        marker_at = source.index(marker)
        call_at = source.rfind("Text(", 0, marker_at)
        if call_at < 0:
            raise SystemExit(f"could not find Text() containing {marker!r}")
        open_at = source.find("(", call_at)
        depth = 0
        i = open_at
        in_string = False
        escaped = False
        while i < len(source):
            ch = source[i]
            if in_string:
                if escaped:
                    escaped = False
                elif ch == "\\\\":
                    escaped = True
                elif ch == '"':
                    in_string = False
            else:
                if ch == '"':
                    in_string = True
                elif ch == "(":
                    depth += 1
                elif ch == ")":
                    depth -= 1
                    if depth == 0:
                        end = i + 1
                        line_start = source.rfind("\\n", 0, call_at) + 1
                        line_end = source.find("\\n", end)
                        if line_end < 0:
                            line_end = end
                        else:
                            line_end += 1
                        source = source[:line_start] + source[line_end:]
                        removed += 1
                        break
            i += 1
        else:
            raise SystemExit(f"unterminated Text() containing {marker!r}")
    return source, removed

home = root / "app/src/main/java/com/night/later/ui/home/HomeScreen.kt"
h = remove_text_call(home.read_text(), "Settings stays on Home.")
h = remove_text_call(h, "You started this, but haven't sent it yet.")
home.write_text(h)

# Older source snapshots also carried the same draft-helper sentence in a
# secondary home composable. Remove every remaining static Compose Text call
# containing it so production QA cannot regress when that path is rendered.
draft_helper = "You started this, but haven't sent it yet."
for kotlin in root.glob("app/src/main/java/**/*.kt"):
    if kotlin == home:
        continue
    source = kotlin.read_text()
    if draft_helper not in source:
        continue
    cleaned, removed = remove_all_text_calls(source, draft_helper)
    if removed:
        kotlin.write_text(cleaned)

remaining_helper_paths = [
    str(path.relative_to(root))
    for path in root.rglob("*")
    if path.is_file()
    and path.suffix in {".kt", ".xml"}
    and draft_helper in path.read_text(errors="ignore")
]
if remaining_helper_paths:
    for relative in remaining_helper_paths:
        path = root / relative
        lines = path.read_text(errors="ignore").splitlines()
        for index, line in enumerate(lines):
            if draft_helper in line:
                start = max(0, index - 8)
                end = min(len(lines), index + 9)
                print(f"--- {relative}:{index + 1} ---", file=sys.stderr)
                for line_no in range(start, end):
                    print(f"{line_no + 1:5}: {lines[line_no]}", file=sys.stderr)
    raise SystemExit(
        "redundant Continue writing helper copy remains in: "
        + ", ".join(remaining_helper_paths)
    )

settings = root / "app/src/main/java/com/night/later/ui/settings/SettingsScreen.kt"
s = remove_text_call(settings.read_text(), "Make Later feel like yours.")
settings.write_text(s)
for forbidden in (
    "Everything has a place now.",
    "Export encrypted archive",
    "What Later protects, and what an offline app cannot promise.",
    "AES-256-GCM",
    "Android Keystore",
    "block manifest",
    "ciphertext archive",
):
    if forbidden in s:
        raise SystemExit(f"production UI still exposes internal copy: {forbidden}")
if 'title =\n                                "Security"' in s:
    raise SystemExit("redundant Security-only settings row remains")
for required in ("Create backup", "Restore backup", "Manage storage and keep a recoverable backup."):
    if required not in s:
        raise SystemExit(f"missing production storage action: {required}")

# PlayerView already provides playback state and timing. Remove the custom
# filename/duration footer so generated storage names never become product UI.
viewer = root / "app/src/main/java/com/night/later/ui/media/LaterMediaViewer.kt"
v = viewer.read_text()
surface_state = """    var playing by remember(player) { mutableStateOf(player.isPlaying) }
    var durationMs by remember(player) { mutableLongStateOf(player.duration.coerceAtLeast(0L)) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    durationMs = player.duration.coerceAtLeast(0L)
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

"""
if v.count(surface_state) != 1:
    raise SystemExit(f"expected one redundant video footer state block, found {v.count(surface_state)}")
v = v.replace(surface_state, "", 1)
surface_footer = """            if (!displayName.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = displayName,
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    if (durationMs > 0L) {
                        Text(
                            text = formatMediaDuration(durationMs),
                            color = Color.White.copy(alpha = 0.66f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
"""
if v.count(surface_footer) != 1:
    raise SystemExit(f"expected one redundant video footer UI block, found {v.count(surface_footer)}")
v = v.replace(surface_footer, "", 1)
viewer.write_text(v)

# The media editors already expose explicit Original/Edited state and trim
# boundaries. Keep the production surfaces concise instead of repeating those
# states as helper copy beside the controls.
image_editor = viewer
i = image_editor.read_text()
image_helper = """            Text(
                text = "Original stays unchanged.",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 3.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
"""
if i.count(image_helper) != 1:
    raise SystemExit(f"expected one redundant image-editor helper block, found {i.count(image_helper)}")
image_editor.write_text(i.replace(image_helper, "", 1))

video_editor = root / "app/src/main/java/com/night/later/ui/media/LaterVideoEditor.kt"
e = video_editor.read_text()
selected_duration = """                Text(
                    "Selected ${formatTrimSpan(endMs - startMs)}",
                    style = MaterialTheme.typography.labelMedium
                )
"""
if e.count(selected_duration) != 1:
    raise SystemExit(f"expected one redundant selected-duration block, found {e.count(selected_duration)}")
video_editor.write_text(e.replace(selected_duration, "", 1))
