package eu.kanade.presentation.more.settings.screen.appearance

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.AppThemePreviewItem
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.isDynamicColorAvailable
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

/**
 * A spacious, scrollable preview gallery that leaves the chapter reader untouched.
 * Only one theme is selected at a time; custom colors are edited in the studio.
 */
class AppThemeGalleryScreen : Screen() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val preferences = remember { context.appGraph.uiPreferences }
        val currentTheme by preferences.appTheme.collectAsState()
        val amoled by preferences.themeDarkAmoled.collectAsState()
        var previewDark by remember { mutableStateOf(true) }
        val themes = remember {
            AppTheme.entries.filterNot {
                it.titleRes == null || (it == AppTheme.MONET && !DeviceUtil.isDynamicColorAvailable)
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.torri_theme_gallery),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { innerPadding ->
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = stringResource(MR.strings.torri_theme_gallery_description),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = { navigator.push(AppThemeStudioScreen()) }) {
                            Text(stringResource(MR.strings.torri_theme_customize))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stringResource(MR.strings.torri_theme_preview_dark))
                            Switch(
                                checked = previewDark,
                                onCheckedChange = { previewDark = it },
                            )
                        }
                    }
                }

                items(items = themes, key = { it.name }) { theme ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TachiyomiTheme(
                            appTheme = theme,
                            amoled = amoled,
                            previewDark = previewDark,
                        ) {
                            AppThemePreviewItem(
                                selected = theme == currentTheme,
                                onClick = {
                                    if (theme == AppTheme.CUSTOM) {
                                        navigator.push(AppThemeStudioScreen())
                                    } else {
                                        preferences.appTheme.set(theme)
                                        (context as? Activity)?.let(ActivityCompat::recreate)
                                    }
                                },
                            )
                        }
                        Text(
                            text = stringResource(theme.titleRes!!),
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            minLines = 2,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}
