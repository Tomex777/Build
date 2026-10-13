package com.night.mirrorchess.ui

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.night.mirrorchess.R

@Composable
private fun VectorGlyph(@DrawableRes resId: Int, color: Color, modifier: Modifier) {
    Icon(
        painter = painterResource(resId),
        contentDescription = null,
        tint = color,
        modifier = modifier,
    )
}

@Composable fun UndoGlyph(color: Color, modifier: Modifier = Modifier) = VectorGlyph(R.drawable.ic_undo_24, color, modifier)
@Composable fun FlipGlyph(color: Color, modifier: Modifier = Modifier) = VectorGlyph(R.drawable.ic_flip_board_24, color, modifier)
@Composable fun NewGameGlyph(color: Color, modifier: Modifier = Modifier) = VectorGlyph(R.drawable.ic_new_game_24, color, modifier)
@Composable fun ChevronGlyph(color: Color, modifier: Modifier = Modifier) = VectorGlyph(R.drawable.ic_chevron_down_24, color, modifier)
@Composable fun LockGlyph(color: Color, modifier: Modifier = Modifier) = VectorGlyph(R.drawable.ic_lock_24, color, modifier)
@Composable fun ResignGlyph(color: Color, modifier: Modifier = Modifier) = VectorGlyph(R.drawable.ic_resign_flag_24, color, modifier)
@Composable fun ExportGlyph(color: Color, modifier: Modifier = Modifier) = VectorGlyph(R.drawable.ic_export_24, color, modifier)
@Composable fun PlayGlyph(color: Color, modifier: Modifier = Modifier) = VectorGlyph(R.drawable.ic_play_24, color, modifier)
@Composable fun MirrorGlyph(color: Color, modifier: Modifier = Modifier) = VectorGlyph(R.drawable.ic_mirror_24, color, modifier)
@Composable fun SettingsGlyph(color: Color, modifier: Modifier = Modifier) = VectorGlyph(R.drawable.ic_settings_24, color, modifier)
