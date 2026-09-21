package com.example.whatsapp.extensions.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NightExtensionSettingsResolverTest {
    @Test
    fun explicitRequestWinsWithoutChangingSavedDefault() {
        val schema = NightExtensionSettingsExamples.anime
        val saved = mapOf(
            "quality" to NightExtensionSettingValue.StringValue("720p"),
            "audio" to NightExtensionSettingValue.StringValue("sub"),
        )
        val task = mapOf(
            "quality" to NightExtensionSettingValue.StringValue("1080p"),
        )
        val explicit = mapOf(
            "quality" to NightExtensionSettingValue.StringValue("360p"),
        )

        val resolved = NightExtensionSettingsResolver.resolve(
            schema = schema,
            explicitUserRequest = explicit,
            taskSpecificOverrides = task,
            savedSettings = saved,
        )

        assertEquals(
            "360p",
            (resolved["quality"] as NightExtensionSettingValue.StringValue).value,
        )
        assertEquals(
            "720p",
            (saved["quality"] as NightExtensionSettingValue.StringValue).value,
        )
    }

    @Test
    fun taskOverrideWinsSavedAndSavedWinsBuiltIn() {
        val schema = NightExtensionSettingsExamples.anime

        val taskResolved = NightExtensionSettingsResolver.resolve(
            schema = schema,
            taskSpecificOverrides = mapOf(
                "quality" to NightExtensionSettingValue.StringValue("1080p"),
            ),
            savedSettings = mapOf(
                "quality" to NightExtensionSettingValue.StringValue("360p"),
            ),
        )
        assertEquals(
            "1080p",
            (taskResolved["quality"] as NightExtensionSettingValue.StringValue).value,
        )

        val savedResolved = NightExtensionSettingsResolver.resolve(
            schema = schema,
            savedSettings = mapOf(
                "quality" to NightExtensionSettingValue.StringValue("360p"),
            ),
        )
        assertEquals(
            "360p",
            (savedResolved["quality"] as NightExtensionSettingValue.StringValue).value,
        )

        val defaults = NightExtensionSettingsResolver.resolve(schema)
        assertEquals(
            "720p",
            (defaults["quality"] as NightExtensionSettingValue.StringValue).value,
        )
        assertEquals(
            "mp4",
            (defaults["container"] as NightExtensionSettingValue.StringValue).value,
        )
    }

    @Test
    fun musicSchemaCoversGenericPrimitiveTypes() {
        val flattened = NightExtensionSettingsExamples.music.settings.flattenSettings()

        assertTrue(flattened.any { it is NightExtensionSettingSpec.Choice })
        assertTrue(flattened.any { it is NightExtensionSettingSpec.Toggle })
        assertTrue(flattened.any { it is NightExtensionSettingSpec.MultiChoice })
        assertTrue(flattened.any { it is NightExtensionSettingSpec.NumberRange })
        assertTrue(flattened.any { it is NightExtensionSettingSpec.Text })
        assertTrue(flattened.any { it is NightExtensionSettingSpec.Action })
        assertTrue(
            NightExtensionSettingsExamples.music.settings.any {
                it is NightExtensionSettingSpec.Advanced
            }
        )
    }
}
