@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.night.sora.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.night.sora.extension.InstalledExtension
import com.night.sora.extension.isCatalogProvider
import com.night.sora.extension.isDiagnosticProvider
import com.night.sora.extension.api.SourceDescriptor
import com.night.sora.model.ContentType
import com.night.sora.ui.theme.*

private data class DefaultSourceTarget(
    val type: ContentType,
    val label: String,
    val description: String,
    val requiredCapability: String,
    val contentKey: String,
)

private data class DefaultSourceOption(
    val extension: InstalledExtension,
    val source: SourceDescriptor,
) {
    val persistedValue: String get() = "${extension.packageName}|${source.id}"
}

@Composable
fun PlayerReaderSettingsScreen(
    extensions: List<InstalledExtension>,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("sora_preferred_sources_v1", Context.MODE_PRIVATE)
    }
    val targets = remember {
        listOf(
            DefaultSourceTarget(ContentType.ANIME, "Anime", "Default extension used to resolve episodes", "episodes", "anime"),
            DefaultSourceTarget(ContentType.MANGA, "Manga", "Default extension used to resolve chapters", "chapters", "manga"),
            DefaultSourceTarget(ContentType.MOVIE, "Movies", "Default extension used to resolve movie streams", "streams", "movie"),
            DefaultSourceTarget(ContentType.TV, "Series", "Default extension used to resolve episodes", "episodes", "tv"),
            DefaultSourceTarget(ContentType.MUSIC, "Music", "Default extension used for catalog, playback and downloads", "streams", "music"),
        )
    }
    var pickerTarget by remember { mutableStateOf<DefaultSourceTarget?>(null) }
    var preferenceEpoch by remember { mutableIntStateOf(0) }

    LaunchedEffect(extensions) {
        val target = targets.first { it.type == ContentType.MUSIC }
        val options = compatibleSources(extensions, target)
        val selected = prefs.getString(preferenceKey(target), null)
        if (options.isNotEmpty() && options.none { it.persistedValue == selected }) {
            prefs.edit().putString(preferenceKey(target), options.first().persistedValue).apply()
            preferenceEpoch++
        }
    }

    Scaffold(
        containerColor = SoraBg,
        topBar = {
            TopAppBar(
                title = { Text("Player & reader") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SoraBg),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Text(
                    "DEFAULT SOURCES",
                    color = SoraAccent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 7.dp),
                )
            }
            targets.forEach { target ->
                item(key = target.type.name) {
                    val options = compatibleSources(extensions, target)
                    val selectedValue = prefs.getString(preferenceKey(target), null)
                    val selected = options.firstOrNull { it.persistedValue == selectedValue }
                    SettingSourceRow(
                        title = target.label,
                        subtitle = selected?.let { "${it.source.name} · ${it.extension.declaredName}" }
                            ?: if (options.isEmpty()) "No compatible source installed" else if (target.type == ContentType.MUSIC) "Select a music source" else "Ask each title",
                        enabled = options.isNotEmpty(),
                        onClick = { pickerTarget = target },
                    )
                    HorizontalDivider(color = androidx.compose.ui.graphics.Color.White.copy(alpha = .055f), modifier = Modifier.padding(start = 64.dp))
                }
            }
            item {
                Text(
                    "Music always uses one selected source at a time. Anime, Manga, Movies and Series can still ask per title when their default is cleared.",
                    color = SoraMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
        }
    }

    val target = pickerTarget
    if (target != null) {
        val options = compatibleSources(extensions, target)
        val selectedValue = prefs.getString(preferenceKey(target), null)
        ModalBottomSheet(
            onDismissRequest = { pickerTarget = null },
            containerColor = SoraSurface,
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 14.dp)) {
                Text(
                    "Default ${target.label.lowercase()} source",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
                if (target.type != ContentType.MUSIC) {
                    SourceChoiceRow(
                        title = "Ask each title",
                        subtitle = "Do not force a default source",
                        selected = selectedValue == null,
                        onClick = {
                            prefs.edit().remove(preferenceKey(target)).apply()
                            preferenceEpoch++
                            pickerTarget = null
                        },
                    )
                }
                options.forEach { option ->
                    SourceChoiceRow(
                        title = option.source.name,
                        subtitle = option.extension.declaredName,
                        selected = selectedValue == option.persistedValue,
                        onClick = {
                            prefs.edit().putString(preferenceKey(target), option.persistedValue).apply()
                            preferenceEpoch++
                            pickerTarget = null
                        },
                    )
                }
            }
        }
    }

    @Suppress("UNUSED_EXPRESSION")
    preferenceEpoch
}

@Composable
private fun SettingSourceRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Source, null, tint = if (enabled) SoraMuted else SoraFaint, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 20.dp)) {
            Text(title, color = if (enabled) SoraText else SoraMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = SoraMuted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 2.dp))
        }
        if (enabled) Icon(Icons.Rounded.ChevronRight, null, tint = SoraFaint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SourceChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Public, null, tint = if (selected) SoraAccent else SoraMuted, modifier = Modifier.size(21.dp))
        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = SoraMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
        }
        if (selected) Icon(Icons.Rounded.Check, "Selected", tint = SoraAccent)
    }
}

private fun compatibleSources(
    extensions: List<InstalledExtension>,
    target: DefaultSourceTarget,
): List<DefaultSourceOption> = extensions.flatMap { extension ->
    if (extension.error != null || extension.isDiagnosticProvider()) {
        emptyList()
    } else {
        extension.descriptor?.sources.orEmpty()
            .filter { source ->
                if (target.type == ContentType.MUSIC) {
                    target.contentKey in source.contentTypes &&
                        extension.isCatalogProvider() &&
                        "streams" in source.capabilities &&
                        ("browse" in source.capabilities || "search" in source.capabilities)
                } else {
                    target.contentKey in source.contentTypes && (
                        target.requiredCapability in source.capabilities ||
                            (!extension.isCatalogProvider() && "search" in source.capabilities)
                    )
                }
            }
            .map { source -> DefaultSourceOption(extension, source) }
    }
}.sortedWith(compareBy({ it.extension.declaredName.lowercase() }, { it.source.name.lowercase() }))

private fun preferenceKey(target: DefaultSourceTarget): String = "preferred_${target.contentKey}"
