package com.example.whatsapp.extensions.settings

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class NightExtensionChoiceOption(
    val value: String,
    val label: String,
)

sealed interface NightExtensionSettingSpec {
    val id: String
    val label: String
    val description: String

    data class Choice(
        override val id: String,
        override val label: String,
        val options: List<NightExtensionChoiceOption>,
        val defaultValue: String,
        override val description: String = "",
    ) : NightExtensionSettingSpec

    data class Toggle(
        override val id: String,
        override val label: String,
        val defaultValue: Boolean,
        override val description: String = "",
    ) : NightExtensionSettingSpec

    data class MultiChoice(
        override val id: String,
        override val label: String,
        val options: List<NightExtensionChoiceOption>,
        val defaultValues: Set<String> = emptySet(),
        override val description: String = "",
    ) : NightExtensionSettingSpec

    data class NumberRange(
        override val id: String,
        override val label: String,
        val min: Double,
        val max: Double,
        val step: Double,
        val defaultValue: Double,
        val unit: String = "",
        override val description: String = "",
    ) : NightExtensionSettingSpec

    data class Text(
        override val id: String,
        override val label: String,
        val defaultValue: String = "",
        val placeholder: String = "",
        val maxLength: Int = 160,
        override val description: String = "",
    ) : NightExtensionSettingSpec

    data class Action(
        override val id: String,
        override val label: String,
        val actionLabel: String,
        override val description: String = "",
    ) : NightExtensionSettingSpec

    data class Advanced(
        override val id: String,
        override val label: String = "More",
        val settings: List<NightExtensionSettingSpec>,
        override val description: String = "",
    ) : NightExtensionSettingSpec
}

data class NightExtensionSettingsSchema(
    val extensionId: String,
    val extensionName: String,
    val settings: List<NightExtensionSettingSpec>,
) {
    init {
        require(extensionId.isNotBlank()) { "extensionId is required." }
        require(extensionName.isNotBlank()) { "extensionName is required." }
        val ids = settings.flattenSettings().map { it.id }
        require(ids.none { it.isBlank() }) { "Setting ids cannot be blank." }
        require(ids.size == ids.distinct().size) { "Setting ids must be unique per extension." }
    }

    fun setting(id: String): NightExtensionSettingSpec? =
        settings.flattenSettings().firstOrNull { it.id == id }
}

sealed interface NightExtensionSettingValue {
    data class StringValue(val value: String) : NightExtensionSettingValue
    data class BooleanValue(val value: Boolean) : NightExtensionSettingValue
    data class StringSetValue(val values: Set<String>) : NightExtensionSettingValue
    data class NumberValue(val value: Double) : NightExtensionSettingValue
}

object NightExtensionSettingsResolver {
    /**
     * Runtime precedence is intentionally separate from persistence:
     *
     * explicit user request > task-specific override > saved setting > built-in default.
     *
     * Explicit/task overrides are never written back by this resolver, so a one-off
     * "1080p" request cannot silently change a saved "720p" default.
     */
    fun resolve(
        schema: NightExtensionSettingsSchema,
        explicitUserRequest: Map<String, NightExtensionSettingValue> = emptyMap(),
        taskSpecificOverrides: Map<String, NightExtensionSettingValue> = emptyMap(),
        savedSettings: Map<String, NightExtensionSettingValue> = emptyMap(),
    ): Map<String, NightExtensionSettingValue> {
        val resolved = schema.defaultValues().toMutableMap()
        savedSettings.forEach { (id, value) ->
            schema.setting(id)?.let { spec ->
                sanitizeValue(spec, value)?.let { resolved[id] = it }
            }
        }
        taskSpecificOverrides.forEach { (id, value) ->
            schema.setting(id)?.let { spec ->
                sanitizeValue(spec, value)?.let { resolved[id] = it }
            }
        }
        explicitUserRequest.forEach { (id, value) ->
            schema.setting(id)?.let { spec ->
                sanitizeValue(spec, value)?.let { resolved[id] = it }
            }
        }
        return resolved
    }

    fun sanitizeValue(
        spec: NightExtensionSettingSpec,
        value: NightExtensionSettingValue,
    ): NightExtensionSettingValue? = when (spec) {
        is NightExtensionSettingSpec.Choice -> {
            val raw = (value as? NightExtensionSettingValue.StringValue)?.value ?: return null
            raw.takeIf { candidate -> spec.options.any { it.value == candidate } }
                ?.let(NightExtensionSettingValue::StringValue)
        }

        is NightExtensionSettingSpec.Toggle ->
            value as? NightExtensionSettingValue.BooleanValue

        is NightExtensionSettingSpec.MultiChoice -> {
            val raw = (value as? NightExtensionSettingValue.StringSetValue)?.values ?: return null
            val allowed = spec.options.mapTo(linkedSetOf()) { it.value }
            NightExtensionSettingValue.StringSetValue(raw.filterTo(linkedSetOf()) { it in allowed })
        }

        is NightExtensionSettingSpec.NumberRange -> {
            val raw = (value as? NightExtensionSettingValue.NumberValue)?.value ?: return null
            val clamped = raw.coerceIn(spec.min, spec.max)
            val steps = ((clamped - spec.min) / spec.step).toInt()
            NightExtensionSettingValue.NumberValue(
                (spec.min + (steps * spec.step)).coerceIn(spec.min, spec.max)
            )
        }

        is NightExtensionSettingSpec.Text -> {
            val raw = (value as? NightExtensionSettingValue.StringValue)?.value ?: return null
            NightExtensionSettingValue.StringValue(raw.take(spec.maxLength))
        }

        is NightExtensionSettingSpec.Action,
        is NightExtensionSettingSpec.Advanced -> null
    }
}

class NightExtensionSettingsStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            "night_extension_settings",
            Context.MODE_PRIVATE,
        )

    fun read(schema: NightExtensionSettingsSchema): Map<String, NightExtensionSettingValue> {
        val raw = preferences.getString(schema.extensionId, null) ?: return emptyMap()
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val values = linkedMapOf<String, NightExtensionSettingValue>()

        schema.settings.flattenSettings().forEach { spec ->
            if (!json.has(spec.id)) return@forEach
            decodeValue(spec, json)?.let { decoded ->
                NightExtensionSettingsResolver.sanitizeValue(spec, decoded)?.let {
                    values[spec.id] = it
                }
            }
        }
        return values
    }

    fun write(
        schema: NightExtensionSettingsSchema,
        id: String,
        value: NightExtensionSettingValue,
    ): Boolean {
        val spec = schema.setting(id) ?: return false
        val sanitized = NightExtensionSettingsResolver.sanitizeValue(spec, value) ?: return false
        val json = runCatching {
            JSONObject(preferences.getString(schema.extensionId, "{}") ?: "{}")
        }.getOrElse { JSONObject() }

        encodeValue(json, id, sanitized)
        preferences.edit().putString(schema.extensionId, json.toString()).apply()
        return true
    }

    fun reset(schema: NightExtensionSettingsSchema) {
        preferences.edit().remove(schema.extensionId).apply()
    }

    private fun decodeValue(
        spec: NightExtensionSettingSpec,
        json: JSONObject,
    ): NightExtensionSettingValue? = when (spec) {
        is NightExtensionSettingSpec.Choice,
        is NightExtensionSettingSpec.Text ->
            NightExtensionSettingValue.StringValue(json.optString(spec.id))

        is NightExtensionSettingSpec.Toggle ->
            NightExtensionSettingValue.BooleanValue(json.optBoolean(spec.id))

        is NightExtensionSettingSpec.MultiChoice -> {
            val array = json.optJSONArray(spec.id) ?: JSONArray()
            val values = linkedSetOf<String>()
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(values::add)
            }
            NightExtensionSettingValue.StringSetValue(values)
        }

        is NightExtensionSettingSpec.NumberRange ->
            NightExtensionSettingValue.NumberValue(json.optDouble(spec.id, spec.defaultValue))

        is NightExtensionSettingSpec.Action,
        is NightExtensionSettingSpec.Advanced -> null
    }

    private fun encodeValue(
        json: JSONObject,
        id: String,
        value: NightExtensionSettingValue,
    ) {
        when (value) {
            is NightExtensionSettingValue.StringValue -> json.put(id, value.value)
            is NightExtensionSettingValue.BooleanValue -> json.put(id, value.value)
            is NightExtensionSettingValue.NumberValue -> json.put(id, value.value)
            is NightExtensionSettingValue.StringSetValue -> {
                json.put(
                    id,
                    JSONArray().apply {
                        value.values.sorted().forEach(::put)
                    }
                )
            }
        }
    }
}

fun NightExtensionSettingsSchema.defaultValues(): Map<String, NightExtensionSettingValue> =
    buildMap {
        settings.flattenSettings().forEach { spec ->
            when (spec) {
                is NightExtensionSettingSpec.Choice -> {
                    val fallback = spec.options.firstOrNull()?.value.orEmpty()
                    put(
                        spec.id,
                        NightExtensionSettingValue.StringValue(
                            spec.defaultValue.takeIf { candidate ->
                                spec.options.any { it.value == candidate }
                            } ?: fallback
                        )
                    )
                }

                is NightExtensionSettingSpec.Toggle ->
                    put(spec.id, NightExtensionSettingValue.BooleanValue(spec.defaultValue))

                is NightExtensionSettingSpec.MultiChoice -> {
                    val allowed = spec.options.mapTo(linkedSetOf()) { it.value }
                    put(
                        spec.id,
                        NightExtensionSettingValue.StringSetValue(
                            spec.defaultValues.filterTo(linkedSetOf()) { it in allowed }
                        )
                    )
                }

                is NightExtensionSettingSpec.NumberRange ->
                    put(
                        spec.id,
                        NightExtensionSettingValue.NumberValue(
                            spec.defaultValue.coerceIn(spec.min, spec.max)
                        )
                    )

                is NightExtensionSettingSpec.Text ->
                    put(
                        spec.id,
                        NightExtensionSettingValue.StringValue(
                            spec.defaultValue.take(spec.maxLength)
                        )
                    )

                is NightExtensionSettingSpec.Action,
                is NightExtensionSettingSpec.Advanced -> Unit
            }
        }
    }

fun List<NightExtensionSettingSpec>.flattenSettings(): List<NightExtensionSettingSpec> =
    flatMap { spec ->
        when (spec) {
            is NightExtensionSettingSpec.Advanced -> spec.settings.flattenSettings()
            else -> listOf(spec)
        }
    }
