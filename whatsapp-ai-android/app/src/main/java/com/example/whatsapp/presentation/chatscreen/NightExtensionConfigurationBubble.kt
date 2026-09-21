package com.example.whatsapp.presentation.chatscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.extensions.messages.ExtensionConfigurationActionCodec
import com.example.whatsapp.extensions.messages.ExtensionConfigurationField
import com.example.whatsapp.extensions.messages.ExtensionConfigurationFieldType
import com.example.whatsapp.extensions.messages.NightExtensionConfigurationStore
import org.json.JSONArray
import org.json.JSONObject

private val ExtensionConfigBubble = Color(0xFF242625)
private val ExtensionConfigPanel = Color(0xFF303436)
private val ExtensionConfigText = Color(0xFFECEDEE)
private val ExtensionConfigMuted = Color(0xFF9EA7AB)
private val ExtensionConfigAccent = Color(0xFFCF4A69)

@Composable
fun NightExtensionConfigurationBubble(
    item: ExtensionResultMessage,
    onAction: (messageId: String, actionId: String) -> Unit,
) {
    val snapshot = item.snapshot
    val configuration = snapshot.configuration ?: return
    val context = LocalContext.current
    val saved = remember(snapshot.extensionId, configuration.id) {
        NightExtensionConfigurationStore.read(
            context,
            snapshot.extensionId,
            configuration.id,
        )
    }
    val values = remember(item.id, configuration, saved?.toString()) {
        mutableStateMapOf<String, String>().apply {
            configuration.fields.forEach { field ->
                if (field.type != ExtensionConfigurationFieldType.MultiChoice) {
                    put(field.id, initialScalarValue(field, saved))
                }
            }
        }
    }
    val multiValues = remember(item.id, configuration, saved?.toString()) {
        mutableStateMapOf<String, Set<String>>().apply {
            configuration.fields
                .filter { it.type == ExtensionConfigurationFieldType.MultiChoice }
                .forEach { field ->
                    put(field.id, initialMultiValue(field, saved))
                }
        }
    }
    var advancedVisible by remember(item.id) { mutableStateOf(false) }

    fun currentValuesJson(): JSONObject =
        JSONObject().apply {
            configuration.fields.forEach { field ->
                when (field.type) {
                    ExtensionConfigurationFieldType.MultiChoice -> {
                        put(
                            field.id,
                            JSONArray(
                                multiValues[field.id]
                                    .orEmpty()
                                    .toList()
                                    .sorted()
                            )
                        )
                    }

                    ExtensionConfigurationFieldType.Toggle ->
                        put(field.id, values[field.id].orEmpty().toBooleanStrictOrNull() ?: false)

                    ExtensionConfigurationFieldType.Number,
                    ExtensionConfigurationFieldType.Range -> {
                        val raw = values[field.id].orEmpty()
                        put(field.id, raw.toDoubleOrNull() ?: raw)
                    }

                    ExtensionConfigurationFieldType.Action -> Unit

                    else -> put(field.id, values[field.id].orEmpty())
                }
            }
        }

    fun send(actionId: String) {
        onAction(
            item.id,
            ExtensionConfigurationActionCodec.encode(
                configurationId = configuration.id,
                actionId = actionId,
                values = currentValuesJson(),
            ),
        )
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 350.dp)
                .background(
                    ExtensionConfigBubble,
                    RoundedCornerShape(
                        topStart = 5.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp,
                    ),
                )
                .padding(9.dp)
                .semantics {
                    contentDescription = "Extension configuration " + configuration.id
                },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = Color(0xFF3A3035),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = ExtensionConfigText,
                            modifier = Modifier.size(25.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = snapshot.title,
                        color = ExtensionConfigText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = snapshot.extensionName,
                        color = ExtensionConfigMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    if (snapshot.subtitle.isNotBlank()) {
                        Text(
                            text = snapshot.subtitle,
                            color = ExtensionConfigMuted,
                            fontSize = 11.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                configuration.fields
                    .filter { !it.advanced }
                    .forEach { field ->
                        ExtensionConfigurationFieldContent(
                            field = field,
                            scalarValue = values[field.id].orEmpty(),
                            multiValue = multiValues[field.id].orEmpty(),
                            onScalarChange = { values[field.id] = it },
                            onMultiChange = { multiValues[field.id] = it },
                            onAction = { send(field.id) },
                        )
                    }

                val advanced = configuration.fields.filter { it.advanced }
                if (advanced.isNotEmpty()) {
                    Surface(
                        color = ExtensionConfigPanel,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = "Show advanced extension settings"
                            }
                            .clickable { advancedVisible = !advancedVisible },
                    ) {
                        Text(
                            text = if (advancedVisible) {
                                "Hide " + configuration.advancedLabel
                            } else {
                                configuration.advancedLabel
                            },
                            color = ExtensionConfigAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                        )
                    }

                    if (advancedVisible) {
                        advanced.forEach { field ->
                            ExtensionConfigurationFieldContent(
                                field = field,
                                scalarValue = values[field.id].orEmpty(),
                                multiValue = multiValues[field.id].orEmpty(),
                                onScalarChange = { values[field.id] = it },
                                onMultiChange = { multiValues[field.id] = it },
                                onAction = { send(field.id) },
                            )
                        }
                    }
                }
            }

            Surface(
                color = Color(0xFF9D2142),
                shape = RoundedCornerShape(11.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 11.dp)
                    .semantics {
                        contentDescription = "Save extension configuration"
                    }
                    .clickable { send(configuration.submitActionId) },
            ) {
                Text(
                    text = configuration.submitLabel,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }

            Text(
                text = item.time,
                color = ExtensionConfigMuted,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 5.dp, end = 2.dp),
            )
        }
    }
}

@Composable
private fun ExtensionConfigurationFieldContent(
    field: ExtensionConfigurationField,
    scalarValue: String,
    multiValue: Set<String>,
    onScalarChange: (String) -> Unit,
    onMultiChange: (Set<String>) -> Unit,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ExtensionConfigPanel, RoundedCornerShape(11.dp))
            .padding(10.dp)
            .semantics {
                contentDescription = "Config " + field.id
            },
    ) {
        when (field.type) {
            ExtensionConfigurationFieldType.Toggle -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FieldLabels(field, Modifier.weight(1f))
                    Switch(
                        checked = scalarValue.toBooleanStrictOrNull() ?: false,
                        onCheckedChange = { onScalarChange(it.toString()) },
                        modifier = Modifier.semantics {
                            contentDescription = "Config " + field.id + " toggle"
                        },
                    )
                }
            }

            ExtensionConfigurationFieldType.SingleChoice -> {
                FieldLabels(field)
                Column(
                    modifier = Modifier.padding(top = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    field.options.forEach { option ->
                        val selected = scalarValue == option.id
                        Surface(
                            color = if (selected) {
                                Color(0xFF49313A)
                            } else {
                                Color(0xFF383E41)
                            },
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics {
                                    contentDescription =
                                        "Config " + field.id + " option " + option.id
                                    this.selected = selected
                                }
                                .clickable { onScalarChange(option.id) },
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = option.label,
                                    color = ExtensionConfigText,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Normal
                                    },
                                )
                                if (option.description.isNotBlank()) {
                                    Text(
                                        text = option.description,
                                        color = ExtensionConfigMuted,
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ExtensionConfigurationFieldType.MultiChoice -> {
                FieldLabels(field)
                Column(
                    modifier = Modifier.padding(top = 5.dp),
                ) {
                    field.options.forEach { option ->
                        val selected = option.id in multiValue
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics {
                                    contentDescription =
                                        "Config " + field.id + " option " + option.id
                                    this.selected = selected
                                }
                                .clickable {
                                    onMultiChange(
                                        if (selected) {
                                            multiValue - option.id
                                        } else {
                                            multiValue + option.id
                                        }
                                    )
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = null,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.label,
                                    color = ExtensionConfigText,
                                    fontSize = 11.sp,
                                )
                                if (option.description.isNotBlank()) {
                                    Text(
                                        text = option.description,
                                        color = ExtensionConfigMuted,
                                        fontSize = 9.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ExtensionConfigurationFieldType.Range -> {
                FieldLabels(field)
                val min = field.min ?: 0.0
                val max = field.max ?: 100.0
                val safeMax = if (max <= min) min + 1.0 else max
                val raw = scalarValue.toDoubleOrNull() ?: min
                val current = raw.coerceIn(min, safeMax)
                Text(
                    text = prettyNumber(current),
                    color = ExtensionConfigAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 5.dp),
                )
                Slider(
                    value = current.toFloat(),
                    onValueChange = { value ->
                        val stepped = field.step
                            ?.takeIf { it > 0.0 }
                            ?.let { step ->
                                (kotlin.math.round((value - min) / step) * step + min)
                                    .coerceIn(min, safeMax)
                            }
                            ?: value.toDouble()
                        onScalarChange(stepped.toString())
                    },
                    valueRange = min.toFloat()..safeMax.toFloat(),
                )
            }

            ExtensionConfigurationFieldType.Number,
            ExtensionConfigurationFieldType.Text -> {
                FieldLabels(field)
                OutlinedTextField(
                    value = scalarValue,
                    onValueChange = { value ->
                        onScalarChange(
                            if (field.type == ExtensionConfigurationFieldType.Number) {
                                value.filter { it.isDigit() || it == '.' || it == '-' }
                            } else {
                                value
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    placeholder = {
                        if (field.placeholder.isNotBlank()) {
                            Text(field.placeholder)
                        }
                    },
                    singleLine = field.type == ExtensionConfigurationFieldType.Number,
                )
            }

            ExtensionConfigurationFieldType.Action -> {
                FieldLabels(field)
                Surface(
                    color = Color(0xFF3A4144),
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 7.dp)
                        .clickable { onAction() },
                ) {
                    Text(
                        text = field.actionLabel.ifBlank { field.label },
                        color = ExtensionConfigText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldLabels(
    field: ExtensionConfigurationField,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = field.label,
            color = ExtensionConfigText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        if (field.description.isNotBlank()) {
            Text(
                text = field.description,
                color = ExtensionConfigMuted,
                fontSize = 9.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun initialScalarValue(
    field: ExtensionConfigurationField,
    saved: JSONObject?,
): String {
    if (field.taskOverride && field.value.isNotBlank()) {
        return field.value
    }
    if (saved != null && saved.has(field.id) && !saved.isNull(field.id)) {
        return saved.opt(field.id)?.toString().orEmpty()
    }
    return field.value
}

private fun initialMultiValue(
    field: ExtensionConfigurationField,
    saved: JSONObject?,
): Set<String> {
    if (field.taskOverride && field.values.isNotEmpty()) {
        return field.values.toSet()
    }

    val savedArray = saved?.optJSONArray(field.id)
    if (savedArray != null) {
        return buildSet {
            for (index in 0 until savedArray.length()) {
                val value = savedArray.optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }

    return field.values.toSet()
}

private fun prettyNumber(value: Double): String =
    if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        String.format(java.util.Locale.getDefault(), "%.2f", value)
            .trimEnd('0')
            .trimEnd('.')
    }
