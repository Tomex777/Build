package com.example.whatsapp.extensions.messages

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NightExtensionConfigurationMessageTest {
    @Test
    fun configurationCardRoundTripsAllFieldFamilies() {
        val snapshot = ExtensionMessageSnapshot(
            extensionId = "anime",
            messageType = "anime.download_configuration",
            template = ExtensionCardTemplate.Configuration,
            extensionName = "Anime Extension",
            title = "Download settings",
            configuration = ExtensionConfiguration(
                id = "download",
                submitActionId = "save_download_settings",
                submitLabel = "Save settings",
                fields = listOf(
                    ExtensionConfigurationField(
                        id = "subtitles",
                        label = "Subtitles",
                        type = ExtensionConfigurationFieldType.Toggle,
                        value = "true",
                    ),
                    ExtensionConfigurationField(
                        id = "resolution",
                        label = "Resolution",
                        type = ExtensionConfigurationFieldType.SingleChoice,
                        value = "720p",
                        options = listOf(
                            ExtensionConfigurationOption("1080p", "1080p"),
                            ExtensionConfigurationOption("720p", "720p"),
                        ),
                    ),
                    ExtensionConfigurationField(
                        id = "formats",
                        label = "Formats",
                        type = ExtensionConfigurationFieldType.MultiChoice,
                        values = listOf("lossless", "aac"),
                        options = listOf(
                            ExtensionConfigurationOption("lossless", "Lossless"),
                            ExtensionConfigurationOption("aac", "AAC"),
                        ),
                    ),
                    ExtensionConfigurationField(
                        id = "parallel",
                        label = "Parallel",
                        type = ExtensionConfigurationFieldType.Number,
                        value = "2",
                    ),
                    ExtensionConfigurationField(
                        id = "quality",
                        label = "Quality",
                        type = ExtensionConfigurationFieldType.Range,
                        value = "70",
                        min = 0.0,
                        max = 100.0,
                        step = 5.0,
                        advanced = true,
                    ),
                    ExtensionConfigurationField(
                        id = "template",
                        label = "Template",
                        type = ExtensionConfigurationFieldType.Text,
                        value = "{title}",
                        taskOverride = true,
                    ),
                    ExtensionConfigurationField(
                        id = "test",
                        label = "Test",
                        type = ExtensionConfigurationFieldType.Action,
                        actionLabel = "Run test",
                        advanced = true,
                    ),
                ),
            ),
        )

        val decoded = ExtensionMessageCodec.decode(
            ExtensionMessageCodec.encode(snapshot)
        )

        assertNotNull(decoded)
        requireNotNull(decoded)
        assertEquals(ExtensionCardTemplate.Configuration, decoded.template)
        assertEquals("download", decoded.configuration?.id)
        assertEquals("save_download_settings", decoded.configuration?.submitActionId)
        assertEquals(7, decoded.configuration?.fields?.size)

        val quality = decoded.configuration?.fields?.first { it.id == "quality" }
        assertEquals(ExtensionConfigurationFieldType.Range, quality?.type)
        assertEquals(0.0, quality?.min ?: -1.0, 0.0)
        assertEquals(100.0, quality?.max ?: -1.0, 0.0)
        assertEquals(5.0, quality?.step ?: -1.0, 0.0)
        assertTrue(quality?.advanced == true)

        val template = decoded.configuration?.fields?.first { it.id == "template" }
        assertTrue(template?.taskOverride == true)
    }

    @Test
    fun configurationSubmissionCarriesTypedValues() {
        val values = JSONObject()
            .put("subtitles", false)
            .put("resolution", "1080p")
            .put("parallel", 3)
            .put("quality", 85.0)
            .put("formats", org.json.JSONArray().put("lossless").put("aac"))

        val encoded = ExtensionConfigurationActionCodec.encode(
            configurationId = "download",
            actionId = "save_download_settings",
            values = values,
        )
        val decoded = ExtensionConfigurationActionCodec.decode(encoded)

        requireNotNull(decoded)
        assertEquals("download", decoded.configurationId)
        assertEquals("save_download_settings", decoded.actionId)

        val decodedValues = JSONObject(decoded.valuesJson)
        assertFalse(decodedValues.getBoolean("subtitles"))
        assertEquals("1080p", decodedValues.getString("resolution"))
        assertEquals(3, decodedValues.getInt("parallel"))
        assertEquals(85.0, decodedValues.getDouble("quality"), 0.0)
        assertEquals(2, decodedValues.getJSONArray("formats").length())
    }

    @Test
    fun applyingSubmittedValuesUpdatesSnapshotWithoutChangingNamespace() {
        val snapshot = ExtensionMessageSnapshot(
            extensionId = "notion",
            messageType = "notion.settings",
            template = ExtensionCardTemplate.Configuration,
            extensionName = "Notion",
            title = "Settings",
            configuration = ExtensionConfiguration(
                id = "workspace",
                fields = listOf(
                    ExtensionConfigurationField(
                        id = "sync",
                        label = "Sync",
                        type = ExtensionConfigurationFieldType.Toggle,
                        value = "true",
                    ),
                    ExtensionConfigurationField(
                        id = "areas",
                        label = "Areas",
                        type = ExtensionConfigurationFieldType.MultiChoice,
                        values = listOf("pages"),
                    ),
                ),
            ),
        )

        val updated = snapshot.withConfigurationValues(
            JSONObject()
                .put("sync", false)
                .put("areas", org.json.JSONArray().put("pages").put("databases"))
        )

        assertEquals("notion.settings", updated.messageType)
        assertTrue(updated.hasValidNamespace())
        assertEquals(
            "false",
            updated.configuration?.fields?.first { it.id == "sync" }?.value,
        )
        assertEquals(
            listOf("pages", "databases"),
            updated.configuration?.fields?.first { it.id == "areas" }?.values,
        )
    }

    @Test
    fun parallelDownloadsIsOnlyAGenericNumberField() {
        val snapshot =
            ExtensionMessageSnapshot(
                extensionId = "video_provider",
                messageType = "video_provider.settings",
                template = ExtensionCardTemplate.Configuration,
                extensionName = "Video Provider",
                title = "Settings",
                configuration =
                    ExtensionConfiguration(
                        id = "downloads",
                        fields =
                            listOf(
                                ExtensionConfigurationField(
                                    id = "parallel_downloads",
                                    label = "Parallel downloads",
                                    type = ExtensionConfigurationFieldType.Number,
                                    value = "2",
                                    min = 1.0,
                                    max = 8.0,
                                    suffix = "downloads",
                                )
                            ),
                    ),
            )

        val decoded =
            requireNotNull(
                ExtensionMessageCodec.decode(
                    ExtensionMessageCodec.encode(snapshot)
                )
            )
        val field =
            requireNotNull(
                decoded.configuration
                    ?.fields
                    ?.singleOrNull()
            )

        assertEquals("parallel_downloads", field.id)
        assertEquals(ExtensionConfigurationFieldType.Number, field.type)
        assertEquals("2", field.value)
        assertEquals("downloads", field.suffix)
    }

    @Test
    fun musicStyleConfigurationUsesSameGenericSectionsAndFields() {
        val snapshot =
            ExtensionMessageSnapshot(
                extensionId = "music_provider",
                messageType = "music_provider.settings",
                template = ExtensionCardTemplate.Configuration,
                extensionName = "Music Provider",
                title = "Music settings",
                configuration =
                    ExtensionConfiguration(
                        id = "music",
                        sections =
                            listOf(
                                ExtensionConfigurationSection(
                                    id = "playback",
                                    title = "Playback",
                                    description = "Audio behaviour.",
                                ),
                                ExtensionConfigurationSection(
                                    id = "lyrics",
                                    title = "Lyrics",
                                    description = "Lyrics behaviour.",
                                ),
                            ),
                        fields =
                            listOf(
                                ExtensionConfigurationField(
                                    id = "quality",
                                    label = "Audio quality",
                                    type = ExtensionConfigurationFieldType.SingleChoice,
                                    value = "lossless",
                                    sectionId = "playback",
                                    options =
                                        listOf(
                                            ExtensionConfigurationOption(
                                                "lossless",
                                                "Lossless",
                                            ),
                                            ExtensionConfigurationOption(
                                                "high",
                                                "High",
                                            ),
                                        ),
                                ),
                                ExtensionConfigurationField(
                                    id = "normalize",
                                    label = "Normalize volume",
                                    type = ExtensionConfigurationFieldType.Toggle,
                                    value = "true",
                                    sectionId = "playback",
                                ),
                                ExtensionConfigurationField(
                                    id = "lyrics_provider",
                                    label = "Lyrics provider",
                                    type = ExtensionConfigurationFieldType.Text,
                                    value = "auto",
                                    sectionId = "lyrics",
                                ),
                                ExtensionConfigurationField(
                                    id = "notes",
                                    label = "Custom lyrics notes",
                                    type = ExtensionConfigurationFieldType.MultilineText,
                                    sectionId = "lyrics",
                                ),
                                ExtensionConfigurationField(
                                    id = "api_token",
                                    label = "Provider token",
                                    type = ExtensionConfigurationFieldType.Secret,
                                    sectionId = "lyrics",
                                    required = true,
                                ),
                            ),
                    ),
            )

        val decoded =
            requireNotNull(
                ExtensionMessageCodec.decode(
                    ExtensionMessageCodec.encode(snapshot)
                )
            )
        val configuration = requireNotNull(decoded.configuration)

        assertEquals(listOf("playback", "lyrics"), configuration.sections.map { it.id })
        assertEquals(
            ExtensionConfigurationFieldType.MultilineText,
            configuration.fields.first { it.id == "notes" }.type,
        )
        assertEquals(
            ExtensionConfigurationFieldType.Secret,
            configuration.fields.first { it.id == "api_token" }.type,
        )
        assertTrue(
            configuration.fields.first { it.id == "api_token" }.required
        )
    }

    @Test
    fun unrelatedActionDoesNotDecodeAsConfigurationSubmission() {
        assertEquals(null, ExtensionConfigurationActionCodec.decode("open"))
    }
}
