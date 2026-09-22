package com.example.whatsapp.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.data.night.NightAppearanceEntity

private val ScreenBg = Color(0xFF0B0F11)
private val TextMain = Color(0xFFE7EAEC)
private val TextMuted = Color(0xFF9CA5A9)
private val Accent = Color(0xFF21C063)

private data class ColorChoice(val name: String, val argb: Long)
private data class WallpaperChoice(
    val name: String,
    val top: Long,
    val middle: Long,
    val bottom: Long,
)

private val bubbleColors = listOf(
    ColorChoice("Wine", 0xFF7E112EL),
    ColorChoice("Purple", 0xFF6D3CC3L),
    ColorChoice("Blue", 0xFF2457C5L),
    ColorChoice("Green", 0xFF176B4DL),
    ColorChoice("Teal", 0xFF0F766EL),
    ColorChoice("Charcoal", 0xFF34383BL),
)

private val wallpapers = listOf(
    WallpaperChoice("Night red", 0xFF6A0011L, 0xFF8F0018L, 0xFF4F000EL),
    WallpaperChoice("Black", 0xFF090A0BL, 0xFF101214L, 0xFF050606L),
    WallpaperChoice("Indigo", 0xFF111A3AL, 0xFF1D2B64L, 0xFF090E22L),
    WallpaperChoice("Forest", 0xFF0E2A22L, 0xFF164A3AL, 0xFF071914L),
    WallpaperChoice("Purple", 0xFF25103CL, 0xFF4A1D68L, 0xFF14091FL),
)

@Composable
fun NightAppearanceScreen(
    appearance: NightAppearanceEntity,
    onBack: () -> Unit,
    onUpdate: (NightAppearanceEntity) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = TextMain)
            }
            Text("Appearance", color = TextMain, fontSize = 22.sp)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                "These settings are native to Night. The AI can change the same values when you ask it to.",
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )

            SectionTitle("Your bubble")
            ColorChoices(
                choices = bubbleColors,
                selected = appearance.userBubbleColor,
                onSelected = { onUpdate(appearance.copy(userBubbleColor = it)) },
            )

            SectionTitle("Night bubble")
            ColorChoices(
                choices = bubbleColors,
                selected = appearance.aiBubbleColor,
                onSelected = { onUpdate(appearance.copy(aiBubbleColor = it)) },
            )

            SectionTitle("Chat wallpaper")
            wallpapers.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onUpdate(
                                appearance.copy(
                                    wallpaperTopColor = item.top,
                                    wallpaperMiddleColor = item.middle,
                                    wallpaperBottomColor = item.bottom,
                                )
                            )
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(item.top, item.middle, item.bottom).forEach { raw ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(Color(raw.toInt()), CircleShape)
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(item.name, color = TextMain, fontSize = 14.sp)
                }
            }

            SectionTitle("Message font size")
            Text(
                ((appearance.messageFontScale * 100).toInt()).toString() + "%",
                color = Accent,
                fontSize = 13.sp,
            )
            Slider(
                value = appearance.messageFontScale,
                onValueChange = {
                    onUpdate(appearance.copy(messageFontScale = it))
                },
                valueRange = 0.85f..1.30f,
                steps = 8,
            )

            SectionTitle("Font")
            listOf(
                "system" to "System",
                "serif" to "Serif",
                "monospace" to "Monospace",
                "cursive" to "Cursive",
            ).forEach { (key, label) ->
                val family = when (key) {
                    "serif" -> FontFamily.Serif
                    "monospace" -> FontFamily.Monospace
                    "cursive" -> FontFamily.Cursive
                    else -> FontFamily.Default
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUpdate(appearance.copy(fontFamilyKey = key)) }
                        .padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        color = if (appearance.fontFamilyKey == key) Accent else TextMain,
                        fontSize = 15.sp,
                        fontFamily = family,
                        fontWeight = if (appearance.fontFamilyKey == key) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = TextMain,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ColorChoices(
    choices: List<ColorChoice>,
    selected: Long,
    onSelected: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        choices.forEach { choice ->
            Column(
                modifier = Modifier.clickable { onSelected(choice.argb) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    color = Color(choice.argb.toInt()),
                    shape = CircleShape,
                    modifier = Modifier.size(if (selected == choice.argb) 42.dp else 36.dp),
                ) {}
                Text(
                    choice.name,
                    color = if (selected == choice.argb) Accent else TextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
    }
}
