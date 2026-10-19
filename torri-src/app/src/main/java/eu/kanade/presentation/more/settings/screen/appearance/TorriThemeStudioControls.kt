package eu.kanade.presentation.more.settings.screen.appearance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.colorscheme.CustomColorScheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** Torri-designed segmented selector; no platform switch/slider or stock filled chips. */
@Composable
internal fun TorriStudioSegments(
    titles: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        titles.forEachIndexed { index, title ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = if (active) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        shape = RoundedCornerShape(13.dp),
                    )
                    .border(
                        1.dp,
                        if (active) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        RoundedCornerShape(13.dp),
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 11.dp, horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (active) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * Editable HEX field is a Torri surface containing a raw Compose BasicTextField.
 * Not Android's text input dialog or a generic Material OutlinedTextField.
 */
@Composable
internal fun TorriHexField(
    value: String,
    isValid: Boolean,
    onValueChange: (String) -> Unit,
    previewColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = stringResource(MR.strings.torri_theme_hex),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                .border(
                    width = 1.dp,
                    color = if (isValid) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.error,
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = { onValueChange(it.take(7).uppercase()) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                textStyle = MaterialTheme.typography.titleSmall.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .size(27.dp)
                    .background(previewColor, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
        }
        if (!isValid) {
            Text(
                text = stringResource(MR.strings.torri_theme_hex_error),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Isolated sample of the chosen color roles. Does not alter the live Library screen. */
@Composable
internal fun TorriThemeLivePreview(modifier: Modifier = Modifier) {
    val palette = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = palette.background,
        border = BorderStroke(1.dp, palette.outlineVariant),
        tonalElevation = 0.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 15.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(MR.strings.label_library),
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.onBackground,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(palette.primary, palette.secondary, palette.tertiary).forEach { swatch ->
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(swatch, CircleShape),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 15.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                listOf(
                    listOf(palette.primaryContainer, palette.secondaryContainer),
                    listOf(palette.tertiaryContainer, palette.primary),
                    listOf(palette.secondaryContainer, palette.tertiary),
                ).forEachIndexed { index, colors ->
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .background(
                                    brush = Brush.verticalGradient(colors),
                                    shape = RoundedCornerShape(9.dp),
                                ),
                            contentAlignment = Alignment.BottomStart,
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(7.dp)
                                    .fillMaxWidth(0.75f)
                                    .height(6.dp)
                                    .background(palette.onPrimaryContainer.copy(alpha = 0.55f), CircleShape),
                            )
                        }
                        Text(
                            text = stringResource(MR.strings.torri_theme_preview_series, index + 1),
                            color = palette.onSurface,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 11.dp)
                    .background(palette.surfaceContainer, RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                    .padding(horizontal = 15.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(
                    MR.strings.label_library,
                    MR.strings.label_recent_updates,
                    MR.strings.history,
                    MR.strings.browse,
                    MR.strings.label_more,
                ).forEachIndexed { index, label ->
                    Text(
                        text = stringResource(label),
                        color = if (index == 0) palette.primary else palette.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
