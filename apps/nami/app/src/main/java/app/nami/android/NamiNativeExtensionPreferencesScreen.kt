@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.nami.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.nami.source.NamiSourceSetting

@Composable
internal fun NamiNativeExtensionPreferencesScreen(
    sourceName: String,
    settings: List<NamiSourceSetting>,
    preferenceHandle: NamiNativeConfigurationHandle,
    onBack: () -> Unit,
) {
    val values = remember(preferenceHandle) { mutableStateMapOf<String, String>() }
    LaunchedEffect(settings, preferenceHandle) {
        settings.forEach { setting ->
            val stored = preferenceHandle.getPreference(setting.key)
            if (stored != null) {
                values[setting.key] = stored
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(sourceName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
        ) {
            if (settings.isEmpty()) {
                item {
                    Text(
                        text = "This source has no settings.",
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(settings, key = { it.key }) { setting ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    when (setting) {
                        is NamiSourceSetting.Toggle -> {
                            val checked = values[setting.key]?.toBooleanStrictOrNull()
                                ?: setting.defaultValue
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(setting.title, style = MaterialTheme.typography.bodyLarge)
                                    setting.summary?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Switch(
                                    checked = checked,
                                    onCheckedChange = { value ->
                                        values[setting.key] = value.toString()
                                        preferenceHandle.putPreference(setting.key, value.toString())
                                    },
                                )
                            }
                        }

                        is NamiSourceSetting.Text -> {
                            val value = values[setting.key] ?: setting.defaultValue
                            OutlinedTextField(
                                value = value,
                                onValueChange = { updated ->
                                    values[setting.key] = updated
                                    preferenceHandle.putPreference(setting.key, updated)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(setting.title) },
                                supportingText = setting.summary?.let { summary ->
                                    { Text(summary) }
                                },
                                singleLine = true,
                                visualTransformation = if (setting.secret) {
                                    PasswordVisualTransformation()
                                } else {
                                    androidx.compose.ui.text.input.VisualTransformation.None
                                },
                            )
                        }

                        is NamiSourceSetting.Choice -> {
                            Text(setting.title, style = MaterialTheme.typography.bodyLarge)
                            setting.summary?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            val selected = values[setting.key]
                                ?: setting.defaultValue
                                ?: setting.choices.firstOrNull().orEmpty()
                            setting.choices.forEach { choice ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            values[setting.key] = choice
                                            preferenceHandle.putPreference(setting.key, choice)
                                        }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = selected == choice,
                                        onClick = {
                                            values[setting.key] = choice
                                            preferenceHandle.putPreference(setting.key, choice)
                                        },
                                    )
                                    Text(choice, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
