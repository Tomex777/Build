package com.example.whatsapp.extensions.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val ExtensionPanel = Color(0xFF151B1E)
private val ExtensionCard = Color(0xFF20272A)
private val ExtensionMuted = Color(0xFF9EA7AB)
private val ExtensionAccent = Color(0xFFE94B72)

@Composable
fun NightExtensionSettingsPanel(
    schema: NightExtensionSettingsSchema,
    store: NightExtensionSettingsStore,
    modifier: Modifier = Modifier,
    onAction: (extensionId: String, actionId: String) -> Unit = { _, _ -> },
) {
    var savedValues by remember(schema.extensionId) {
        mutableStateOf(store.read(schema))
    }
    val advancedOpen = remember(schema.extensionId) { mutableStateMapOf<String, Boolean>() }

    fun effective(): Map<String, NightExtensionSettingValue> =
        NightExtensionSettingsResolver.resolve(
            schema = schema,
            savedSettings = savedValues,
        )

    fun persist(id: String, value: NightExtensionSettingValue) {
        if (store.write(schema, id, value)) {
            savedValues = store.read(schema)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ExtensionPanel)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = schema.extensionName,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Extension settings",
                    color = ExtensionMuted,
                    fontSize = 12.sp,
                )
            }
            TextButton(
                onClick = {
                    store.reset(schema)
                    savedValues = emptyMap()
                },
                modifier = Modifier.semantics {
                    contentDescription = "Reset ${schema.extensionName} settings"
                },
            ) {
                Text("Reset", color = ExtensionAccent)
            }
        }

        val values = effective()
        schema.settings.forEach { spec ->
            NightExtensionSettingControl(
                schema = schema,
                spec = spec,
                values = values,
                advancedOpen = advancedOpen,
                onValueChanged = ::persist,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun NightExtensionSettingControl(
    schema: NightExtensionSettingsSchema,
    spec: NightExtensionSettingSpec,
    values: Map<String, NightExtensionSettingValue>,
    advancedOpen: MutableMap<String, Boolean>,
    onValueChanged: (String, NightExtensionSettingValue) -> Unit,
    onAction: (extensionId: String, actionId: String) -> Unit,
) {
    when (spec) {
        is NightExtensionSettingSpec.Choice -> {
            if (spec.options.size <= 1) return

            SettingCard(spec.label, spec.description) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val selected =
                        (values[spec.id] as? NightExtensionSettingValue.StringValue)?.value
                            ?: spec.defaultValue
                    spec.options.forEach { option ->
                        SettingOptionRow(
                            label = option.label,
                            selected = selected == option.value,
                            contentDescription = "${spec.label}: ${option.label}",
                            onClick = {
                                onValueChanged(
                                    spec.id,
                                    NightExtensionSettingValue.StringValue(option.value),
                                )
                            },
                        )
                    }
                }
            }
        }

        is NightExtensionSettingSpec.Toggle -> {
            val checked =
                (values[spec.id] as? NightExtensionSettingValue.BooleanValue)?.value
                    ?: spec.defaultValue
            SettingCard(spec.label, spec.description) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (checked) "On" else "Off",
                        color = ExtensionMuted,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = checked,
                        onCheckedChange = {
                            onValueChanged(
                                spec.id,
                                NightExtensionSettingValue.BooleanValue(it),
                            )
                        },
                        modifier = Modifier.semantics {
                            contentDescription = spec.label
                        },
                    )
                }
            }
        }

        is NightExtensionSettingSpec.MultiChoice -> {
            val selected =
                (values[spec.id] as? NightExtensionSettingValue.StringSetValue)?.values
                    ?: spec.defaultValues
            SettingCard(spec.label, spec.description) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    spec.options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics {
                                    contentDescription = "${spec.label}: ${option.label}"
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = option.value in selected,
                                onCheckedChange = { checked ->
                                    val next = selected.toMutableSet()
                                    if (checked) next += option.value else next -= option.value
                                    onValueChanged(
                                        spec.id,
                                        NightExtensionSettingValue.StringSetValue(next),
                                    )
                                },
                            )
                            Text(option.label, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        is NightExtensionSettingSpec.NumberRange -> {
            val current =
                (values[spec.id] as? NightExtensionSettingValue.NumberValue)?.value
                    ?: spec.defaultValue
            val steps = (((spec.max - spec.min) / spec.step).roundToInt() - 1).coerceAtLeast(0)
            SettingCard(spec.label, spec.description) {
                Column {
                    Text(
                        text = formatNumberValue(current, spec.unit),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Slider(
                        value = current.toFloat(),
                        onValueChange = { raw ->
                            val snappedSteps = ((raw - spec.min) / spec.step).roundToInt()
                            val snapped =
                                (spec.min + snappedSteps * spec.step).coerceIn(spec.min, spec.max)
                            onValueChanged(
                                spec.id,
                                NightExtensionSettingValue.NumberValue(snapped),
                            )
                        },
                        valueRange = spec.min.toFloat()..spec.max.toFloat(),
                        steps = steps,
                        modifier = Modifier.semantics {
                            contentDescription = spec.label
                        },
                    )
                }
            }
        }

        is NightExtensionSettingSpec.Text -> {
            val current =
                (values[spec.id] as? NightExtensionSettingValue.StringValue)?.value
                    ?: spec.defaultValue
            SettingCard(spec.label, spec.description) {
                OutlinedTextField(
                    value = current,
                    onValueChange = {
                        onValueChanged(
                            spec.id,
                            NightExtensionSettingValue.StringValue(it.take(spec.maxLength)),
                        )
                    },
                    placeholder = {
                        if (spec.placeholder.isNotBlank()) {
                            Text(spec.placeholder)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = spec.label },
                )
            }
        }

        is NightExtensionSettingSpec.Action -> {
            SettingCard(spec.label, spec.description) {
                Button(
                    onClick = { onAction(schema.extensionId, spec.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = ExtensionAccent),
                    modifier = Modifier.semantics {
                        contentDescription = spec.actionLabel
                    },
                ) {
                    Text(spec.actionLabel)
                }
            }
        }

        is NightExtensionSettingSpec.Advanced -> {
            val open = advancedOpen[spec.id] == true
            Surface(
                color = ExtensionCard,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    TextButton(
                        onClick = { advancedOpen[spec.id] = !open },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = spec.label },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = spec.label,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (spec.description.isNotBlank()) {
                                    Text(
                                        text = spec.description,
                                        color = ExtensionMuted,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = Color.White,
                            )
                        }
                    }

                    if (open) {
                        Divider(color = Color.White.copy(alpha = 0.08f))
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            spec.settings.forEach { nested ->
                                NightExtensionSettingControl(
                                    schema = schema,
                                    spec = nested,
                                    values = values,
                                    advancedOpen = advancedOpen,
                                    onValueChanged = onValueChanged,
                                    onAction = onAction,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingCard(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Surface(
        color = ExtensionCard,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    color = ExtensionMuted,
                    fontSize = 11.sp,
                )
            }
            content()
        }
    }
}

@Composable
private fun SettingOptionRow(
    label: String,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(min = 48.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = if (selected) ExtensionAccent else Color.White,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (selected) "Selected" else "",
                color = ExtensionMuted,
                fontSize = 10.sp,
            )
        }
    }
}

private fun formatNumberValue(value: Double, unit: String): String {
    val number =
        if (value % 1.0 == 0.0) value.toInt().toString()
        else String.format(java.util.Locale.US, "%.1f", value)
    return if (unit.isBlank()) number else "$number $unit"
}
