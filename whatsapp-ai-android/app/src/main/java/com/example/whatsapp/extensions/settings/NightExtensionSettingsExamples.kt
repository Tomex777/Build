package com.example.whatsapp.extensions.settings

object NightExtensionSettingsExamples {
    val anime = NightExtensionSettingsSchema(
        extensionId = "night.anime",
        extensionName = "Anime",
        settings = listOf(
            NightExtensionSettingSpec.Choice(
                id = "quality",
                label = "Quality",
                description = "Default video quality when the request does not specify one.",
                options = listOf(
                    NightExtensionChoiceOption("360p", "360p"),
                    NightExtensionChoiceOption("720p", "720p"),
                    NightExtensionChoiceOption("1080p", "1080p"),
                ),
                defaultValue = "720p",
            ),
            NightExtensionSettingSpec.Choice(
                id = "audio",
                label = "Audio",
                options = listOf(
                    NightExtensionChoiceOption("sub", "Sub"),
                    NightExtensionChoiceOption("dub", "Dub"),
                ),
                defaultValue = "sub",
            ),
            // Intentionally one value. Night keeps MP4 as the built-in default but
            // the renderer does not show a meaningless selector.
            NightExtensionSettingSpec.Choice(
                id = "container",
                label = "Container",
                options = listOf(
                    NightExtensionChoiceOption("mp4", "MP4"),
                ),
                defaultValue = "mp4",
            ),
            NightExtensionSettingSpec.Toggle(
                id = "prefer_batch",
                label = "Prefer batch downloads",
                description = "Use batch-capable sources when they are available.",
                defaultValue = true,
            ),
            NightExtensionSettingSpec.Advanced(
                id = "advanced",
                label = "More",
                description = "Less common anime defaults.",
                settings = listOf(
                    NightExtensionSettingSpec.Toggle(
                        id = "preserve_source_names",
                        label = "Preserve source filenames",
                        defaultValue = false,
                    ),
                    NightExtensionSettingSpec.Text(
                        id = "download_folder",
                        label = "Download folder",
                        placeholder = "Anime",
                        defaultValue = "Anime",
                        maxLength = 80,
                    ),
                ),
            ),
        ),
    )

    val music = NightExtensionSettingsSchema(
        extensionId = "night.music",
        extensionName = "Music",
        settings = listOf(
            NightExtensionSettingSpec.Choice(
                id = "format",
                label = "Format",
                options = listOf(
                    NightExtensionChoiceOption("m4a", "M4A"),
                    NightExtensionChoiceOption("mp3", "MP3"),
                    NightExtensionChoiceOption("flac", "FLAC"),
                ),
                defaultValue = "m4a",
            ),
            NightExtensionSettingSpec.NumberRange(
                id = "bitrate",
                label = "Bitrate",
                description = "Used for lossy formats.",
                min = 96.0,
                max = 320.0,
                step = 32.0,
                defaultValue = 256.0,
                unit = "kbps",
            ),
            NightExtensionSettingSpec.MultiChoice(
                id = "metadata",
                label = "Metadata",
                description = "Choose metadata Night should keep with downloaded tracks.",
                options = listOf(
                    NightExtensionChoiceOption("cover", "Cover art"),
                    NightExtensionChoiceOption("lyrics", "Lyrics"),
                    NightExtensionChoiceOption("credits", "Credits"),
                ),
                defaultValues = setOf("cover", "credits"),
            ),
            NightExtensionSettingSpec.Toggle(
                id = "normalize",
                label = "Normalize loudness",
                defaultValue = false,
            ),
            NightExtensionSettingSpec.Text(
                id = "playlist_folder",
                label = "Playlist folder",
                placeholder = "Music",
                defaultValue = "Music",
                maxLength = 80,
            ),
            NightExtensionSettingSpec.Action(
                id = "clear_cache",
                label = "Downloaded artwork cache",
                description = "Clears temporary extension artwork without changing saved settings.",
                actionLabel = "Clear cache",
            ),
            NightExtensionSettingSpec.Advanced(
                id = "advanced",
                label = "More",
                description = "Playback and tagging defaults.",
                settings = listOf(
                    NightExtensionSettingSpec.Choice(
                        id = "lyrics_mode",
                        label = "Lyrics",
                        options = listOf(
                            NightExtensionChoiceOption("embedded", "Embed when available"),
                            NightExtensionChoiceOption("sidecar", "Save beside track"),
                            NightExtensionChoiceOption("off", "Off"),
                        ),
                        defaultValue = "embedded",
                    ),
                    NightExtensionSettingSpec.Toggle(
                        id = "gapless",
                        label = "Prefer gapless albums",
                        defaultValue = true,
                    ),
                ),
            ),
        ),
    )
}
