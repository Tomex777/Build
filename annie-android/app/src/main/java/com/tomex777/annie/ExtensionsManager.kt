package com.tomex777.annie

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ExtensionsText = Color(0xFFEEF5FF)
private val ExtensionsMuted = Color(0xFF9CB2CC)
private val ExtensionsBlue = Color(0xFF168EEA)
private val ExtensionsTeal = Color(0xFF54D6AE)
private val ExtensionsSurface = Color(0xFF11243A)
private val ExtensionsBorder = Color(0xFF29425F)

private data class BuiltInProvider(val name: String, val scope: String)

@Composable
internal fun ExtensionsManagerContent(
    projects: List<ScriptProject>,
    onToggle: (ScriptProject, Boolean) -> Unit,
    onConfigure: (ScriptProject) -> Unit,
    onOpenStudio: (ScriptProject?) -> Unit,
    onLearn: () -> Unit = {},
    onInstallExtension: () -> Unit = { onOpenStudio(null) },
    grantedPermissions: (ScriptProject) -> Set<String> = { emptySet() },
) {
    val builtIns = listOf(
        BuiltInProvider("AniList", "Anime & manga metadata"),
        BuiltInProvider("Wikidata", "Movie metadata"),
        BuiltInProvider("TVmaze", "TV metadata"),
    )
    Column(
        Modifier.fillMaxWidth().statusBarsPadding().navigationBarsPadding().heightIn(max = 650.dp)
            .padding(horizontal = 20.dp).padding(bottom = 24.dp)
            .testTag("extensions_manager"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Extensions", color = ExtensionsText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Add commands, media sources, tools and automations.",
                    color = ExtensionsMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Text("Learn", color = ExtensionsBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onLearn).padding(8.dp).testTag("extensions_learn"))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExtensionRouteButton("Install extension", Modifier.weight(1f).testTag("extensions_install"), onInstallExtension)
            ExtensionRouteButton("Create script", Modifier.weight(1f).testTag("extensions_create_script")) { onOpenStudio(null) }
        }

        LazyColumn(
            Modifier.fillMaxWidth().weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            item { Text("Built in", color = ExtensionsMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            items(builtIns, key = { it.name }) { provider ->
                Surface(
                    color = ExtensionsSurface,
                    shape = RoundedCornerShape(15.dp),
                    border = BorderStroke(1.dp, ExtensionsBorder),
                    modifier = Modifier.fillMaxWidth().testTag("extension_builtin_" + provider.name.lowercase()),
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        Box(
                            Modifier.size(34.dp).background(Color(0xFF183553), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(provider.name.take(1), color = ExtensionsBlue, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(provider.name, color = ExtensionsText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(provider.scope, color = ExtensionsMuted, fontSize = 11.sp)
                        }
                    }
                }
            }

            item {
                Text(
                    "Installed packages",
                    color = ExtensionsMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            if (projects.isEmpty()) {
                item {
                    Surface(
                        color = ExtensionsSurface,
                        shape = RoundedCornerShape(15.dp),
                        border = BorderStroke(1.dp, ExtensionsBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "No scripts or packages installed.",
                            color = ExtensionsText,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(15.dp),
                        )
                    }
                }
            } else {
                items(projects, key = { it.id }) { project ->
                    ExtensionProjectCard(project, grantedPermissions(project), onToggle, onConfigure, onOpenStudio)
                }
            }
        }
    }
}

@Composable
private fun ExtensionProjectCard(
    project: ScriptProject,
    grantedPermissions: Set<String>,
    onToggle: (ScriptProject, Boolean) -> Unit,
    onConfigure: (ScriptProject) -> Unit,
    onOpenStudio: (ScriptProject?) -> Unit,
) {
    Surface(
        color = ExtensionsSurface,
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, ExtensionsBorder),
        modifier = Modifier.fillMaxWidth().testTag("extension_project_" + project.id),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        project.manifest.displayName,
                        color = ExtensionsText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (project.hasPackageManifest) {
                            "Package " + project.manifest.version + " · API " + project.manifest.apiVersion
                        } else {
                            "Script · " + project.entryPath
                        },
                        color = ExtensionsMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (project.hasPackageManifest) {
                        Text(
                            project.manifest.packageId,
                            color = ExtensionsMuted,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("extension_package_id_${project.id}"),
                        )
                    }
                }
                Switch(
                    checked = project.enabled,
                    onCheckedChange = { onToggle(project, it) },
                    modifier = Modifier.testTag("extension_toggle_" + project.id),
                )
            }
            if (project.hasPackageManifest) {
                project.manifest.sources.forEach { source ->
                    Text(
                        "${source.displayName} · ${source.mediaTypes.joinToString(", ")}",
                        color = ExtensionsMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("extension_source_${source.id}"),
                    )
                }
                project.manifest.commands.forEach { command ->
                    Text("/${command.name} · ${command.description}", color = ExtensionsMuted, fontSize = 10.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("extension_command_${project.id}_${command.name}"))
                }
                if (project.manifest.permissions.isNotEmpty()) {
                    Text("Permissions", color = ExtensionsMuted, fontSize = 10.sp,
                        modifier = Modifier.testTag("extension_permissions_${project.id}"))
                    project.manifest.permissions.sorted().forEach { permission ->
                        Text("${if (permission in grantedPermissions) "Granted" else "Not granted"} · $permission",
                            color = if (permission in grantedPermissions) ExtensionsTeal else ExtensionsMuted,
                            fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("extension_permission_${project.id}_${permission.replace('.', '_')}"))
                    }
                }
                project.manifest.services.forEach { service ->
                    Text("Service · ${service.name}", color = ExtensionsMuted, fontSize = 10.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    if (project.enabled) "Configure" else "Enable to configure",
                    color = if (project.enabled) ExtensionsBlue else ExtensionsMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable(enabled = project.enabled) { onConfigure(project) }
                        .padding(vertical = 3.dp)
                        .testTag("extension_configure_" + project.id),
                )
                Text(
                    "Open code",
                    color = ExtensionsBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onOpenStudio(project) }.padding(vertical = 3.dp)
                        .testTag("extension_open_" + project.id),
                )
            }
        }
    }
}

@Composable
private fun ExtensionRouteButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = Color(0xFF143758), shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, ExtensionsBorder),
    ) {
        Text(label, color = ExtensionsText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 11.dp), maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}
