#!/usr/bin/env python3
"""Enforce production-facing Later UI cleanup after feature overlays."""
from pathlib import Path
import re
import sys
if len(sys.argv) != 2:
    raise SystemExit("usage: apply-production-ui-cleanup.py SOURCE_ROOT")
root = Path(sys.argv[1])
home = root / "app/src/main/java/com/night/later/ui/home/HomeScreen.kt"
h = home.read_text()
old = "Settings stays on Home."
new = "Appearance, privacy, and storage."
if h.count(old) != 1:
    raise SystemExit(f"expected one Home settings-copy anchor, found {h.count(old)}")
home.write_text(h.replace(old, new, 1))
settings = root / "app/src/main/java/com/night/later/ui/settings/SettingsScreen.kt"
s = settings.read_text()
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
image_editor = root / "app/src/main/java/com/night/later/ui/media/LaterImageEditor.kt"
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
