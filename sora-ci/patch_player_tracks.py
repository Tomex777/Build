from pathlib import Path

path = Path("sora-overlay/app/src/main/java/com/night/sora/ui/screens/VideoPlayerScreen.kt")
text = path.read_text()


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    text = text.replace(old, new, 1)

replace_once(
    "import androidx.media3.common.Player\n",
    "import androidx.media3.common.Player\nimport androidx.media3.common.TrackSelectionOverride\nimport androidx.media3.common.Tracks\n",
    "track imports",
)

replace_once(
    "import kotlin.math.roundToLong\n\n@Composable\n",
    "import kotlin.math.roundToLong\n\nprivate data class PlayerTrackOption(\n    val group: Tracks.Group,\n    val trackIndex: Int,\n    val label: String,\n    val selected: Boolean,\n)\n\n@Composable\n",
    "track option model",
)

replace_once(
    "    var streamMenuOpen by remember { mutableStateOf(false) }\n    var speedMenuOpen by remember { mutableStateOf(false) }\n",
    "    var streamMenuOpen by remember { mutableStateOf(false) }\n    var speedMenuOpen by remember { mutableStateOf(false) }\n    var audioMenuOpen by remember { mutableStateOf(false) }\n    var subtitleMenuOpen by remember { mutableStateOf(false) }\n    var audioOptions by remember { mutableStateOf<List<PlayerTrackOption>>(emptyList()) }\n    var subtitleOptions by remember { mutableStateOf<List<PlayerTrackOption>>(emptyList()) }\n",
    "track menu state",
)

replace_once(
    "            playbackError = player.playerError?.message ?: playbackError\n            delay(250)\n",
    "            playbackError = player.playerError?.message ?: playbackError\n            audioOptions = playerTrackOptions(player.currentTracks, C.TRACK_TYPE_AUDIO)\n            subtitleOptions = playerTrackOptions(player.currentTracks, C.TRACK_TYPE_TEXT)\n            delay(250)\n",
    "refresh track options",
)

replace_once(
    "        if (controlsVisible && isPlaying && !streamMenuOpen && !speedMenuOpen) {\n",
    "        if (controlsVisible && isPlaying && !streamMenuOpen && !speedMenuOpen && !audioMenuOpen && !subtitleMenuOpen) {\n",
    "keep controls while track menus open",
)

old_anchor = '''                        Box {
                            TextButton(onClick = { speedMenuOpen = true; controlsVisible = true }) {
                                Text("${trimSpeed(speed)}×", color = Color.White, fontSize = 11.sp)
                            }
'''

new_anchor = '''                        if (audioOptions.isNotEmpty()) {
                            Box {
                                IconButton(onClick = { audioMenuOpen = true; controlsVisible = true }) {
                                    Icon(Icons.Rounded.Audiotrack, "Audio track", tint = Color.White)
                                }
                                DropdownMenu(expanded = audioMenuOpen, onDismissRequest = { audioMenuOpen = false }) {
                                    audioOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.label) },
                                            trailingIcon = { if (option.selected) Icon(Icons.Rounded.Check, null, tint = SoraAccent) },
                                            onClick = {
                                                player.trackSelectionParameters = player.trackSelectionParameters
                                                    .buildUpon()
                                                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                                                    .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.trackIndex))
                                                    .build()
                                                audioMenuOpen = false
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        if (subtitleOptions.isNotEmpty()) {
                            Box {
                                IconButton(onClick = { subtitleMenuOpen = true; controlsVisible = true }) {
                                    Icon(Icons.Rounded.Subtitles, "Subtitles", tint = Color.White)
                                }
                                DropdownMenu(expanded = subtitleMenuOpen, onDismissRequest = { subtitleMenuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Off") },
                                        trailingIcon = {
                                            if (player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)) {
                                                Icon(Icons.Rounded.Check, null, tint = SoraAccent)
                                            }
                                        },
                                        onClick = {
                                            player.trackSelectionParameters = player.trackSelectionParameters
                                                .buildUpon()
                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                                .build()
                                            subtitleMenuOpen = false
                                        },
                                    )
                                    subtitleOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.label) },
                                            trailingIcon = { if (option.selected) Icon(Icons.Rounded.Check, null, tint = SoraAccent) },
                                            onClick = {
                                                player.trackSelectionParameters = player.trackSelectionParameters
                                                    .buildUpon()
                                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                    .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.trackIndex))
                                                    .build()
                                                subtitleMenuOpen = false
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        Box {
                            TextButton(onClick = { speedMenuOpen = true; controlsVisible = true }) {
                                Text("${trimSpeed(speed)}×", color = Color.White, fontSize = 11.sp)
                            }
'''
replace_once(old_anchor, new_anchor, "track controls UI")

text += '''\nprivate fun playerTrackOptions(tracks: Tracks, type: Int): List<PlayerTrackOption> = buildList {
    var fallbackIndex = 1
    tracks.groups.filter { it.type == type }.forEach { group ->
        for (trackIndex in 0 until group.length) {
            if (!group.isTrackSupported(trackIndex)) continue
            val format = group.getTrackFormat(trackIndex)
            val baseLabel = format.label?.takeIf(String::isNotBlank)
                ?: format.language?.takeIf(String::isNotBlank)?.uppercase()
                ?: if (type == C.TRACK_TYPE_AUDIO) "Audio $fallbackIndex" else "Subtitle $fallbackIndex"
            val details = buildList {
                if (type == C.TRACK_TYPE_AUDIO && format.channelCount > 0) add("${format.channelCount}ch")
                if (format.bitrate > 0) add("${format.bitrate / 1000} kbps")
            }
            add(
                PlayerTrackOption(
                    group = group,
                    trackIndex = trackIndex,
                    label = if (details.isEmpty()) baseLabel else "$baseLabel · ${details.joinToString(" · ")}",
                    selected = group.isTrackSelected(trackIndex),
                )
            )
            fallbackIndex++
        }
    }
}\n'''

path.write_text(text)
print("Player audio/subtitle track selection applied to", path)
