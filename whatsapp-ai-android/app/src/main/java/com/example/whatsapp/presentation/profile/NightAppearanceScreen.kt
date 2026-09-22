package com.example.whatsapp.presentation.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.data.night.NightAppearanceEntity
import com.example.whatsapp.data.night.NightAppearanceFontRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast

private val ScreenBg = Color(0xFF0B0F11)
private val TextMain = Color(0xFFE7EAEC)
private val TextMuted = Color(0xFF9CA5A9)

private data class ColorChoice(val name: String, val argb: Long)
private data class WallpaperChoice(
    val name: String,
    val top: Long,
    val middle: Long,
    val bottom: Long,
)

private val bubbleColors = listOf(
    ColorChoice("Night pink", 0xFFCF4A69L),
    ColorChoice("Wine", 0xFF7E112EL),
    ColorChoice("Purple", 0xFF6D3CC3L),
    ColorChoice("Blue", 0xFF2457C5L),
    ColorChoice("Green", 0xFF176B4DL),
    ColorChoice("Teal", 0xFF0F766EL),
    ColorChoice("Orange", 0xFFA34E12L),
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
    val accent = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var fontRevision by rememberSaveable { mutableIntStateOf(0) }
    val fontPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        NightAppearanceFontRegistry.addFromUri(context, uri)
                    }
                }.onSuccess { font ->
                    fontRevision += 1
                    onUpdate(appearance.copy(fontFamilyKey = font.key))
                }.onFailure { error ->
                    Toast.makeText(
                        context,
                        error.message ?: "Could not load that font.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { NightAppearanceFontRegistry.load(context) }
        fontRevision += 1
    }

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
            AppearancePreview(appearance)

            Text(
                "Changes apply live to Night chats. The same values are available to Night's appearance tools.",
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )

            SectionTitle("Theme")
            Surface(
                color = Color(0xFF171C1F),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, accent),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Dark", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Night's dark chat canvas", color = TextMuted, fontSize = 11.sp)
                    }
                    Icon(Icons.Default.Check, "Selected theme", tint = accent, modifier = Modifier.size(20.dp))
                }
            }

            SectionTitle("Accent")
            ColorChoices(
                choices = bubbleColors,
                selected = appearance.accentColor,
                accent = accent,
                onSelected = { onUpdate(appearance.copy(accentColor = it)) },
            )
            var customAccent by rememberSaveable(appearance.accentColor) {
                mutableStateOf("#%08X".format(appearance.accentColor.toInt()))
            }
            var accentError by rememberSaveable { mutableStateOf(false) }
            OutlinedTextField(
                value = customAccent,
                onValueChange = { value ->
                    customAccent = value
                    val parsed = runCatching {
                        val hex = value.trim().removePrefix("#")
                        require(hex.matches(Regex("(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})")))
                        val argb = if (hex.length == 6) "FF$hex" else hex
                        android.graphics.Color.parseColor("#$argb").toLong() and 0xFFFFFFFFL
                    }.getOrNull()
                    accentError = parsed == null
                    if (parsed != null) onUpdate(appearance.copy(accentColor = parsed))
                },
                label = { Text("Custom accent (hex)") },
                supportingText = { Text(if (accentError) "Enter a 6 or 8 digit hex color." else "Updates Night as you type.") },
                isError = accentError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )

            SectionTitle("Your bubble")
            ColorChoices(
                choices = bubbleColors,
                selected = appearance.userBubbleColor,
                accent = accent,
                onSelected = { onUpdate(appearance.copy(userBubbleColor = it)) },
            )

            SectionTitle("Night bubble")
            ColorChoices(
                choices = bubbleColors,
                selected = appearance.aiBubbleColor,
                accent = accent,
                onSelected = { onUpdate(appearance.copy(aiBubbleColor = it)) },
            )

            SectionTitle("Chat wallpaper")
            wallpapers.forEach { item ->
                val selected =
                    appearance.wallpaperTopColor == item.top &&
                        appearance.wallpaperMiddleColor == item.middle &&
                        appearance.wallpaperBottomColor == item.bottom
                Surface(
                    color = if (selected) accent.copy(alpha = 0.10f) else Color.Transparent,
                    shape = RoundedCornerShape(16.dp),
                    border = if (selected) BorderStroke(1.dp, accent) else null,
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
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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
                        Text(
                            item.name,
                            color = if (selected) accent else TextMain,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(Icons.Default.Check, "Selected wallpaper", tint = accent, modifier = Modifier.size(19.dp))
                        }
                    }
                }
            }

            SectionTitle("Message font size")
            Text(
                ((appearance.messageFontScale * 100).toInt()).toString() + "%",
                color = accent,
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
            val availableFonts = remember(fontRevision) {
                NightAppearanceFontRegistry.available()
            }
            availableFonts.forEach { font ->
                val key = font.key
                val label = font.label
                val family = font.family
                val selected = appearance.fontFamilyKey == key
                Surface(
                    color = if (selected) accent.copy(alpha = 0.10f) else Color.Transparent,
                    shape = RoundedCornerShape(16.dp),
                    border = if (selected) BorderStroke(1.dp, accent) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUpdate(appearance.copy(fontFamilyKey = key)) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label,
                            color = if (selected) accent else TextMain,
                            fontSize = 15.sp,
                            fontFamily = family,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(Icons.Default.Check, "Selected font", tint = accent, modifier = Modifier.size(19.dp))
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream", "*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Add .ttf or .otf font", color = accent)
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AppearancePreview(
    appearance: NightAppearanceEntity,
) {
    val family = NightAppearanceFontRegistry.find(appearance.fontFamilyKey)?.family
        ?: FontFamily.Default
    Surface(
        color = Color(0xFF111719),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(appearance.wallpaperTopColor.toInt()),
                            Color(appearance.wallpaperMiddleColor.toInt()),
                            Color(appearance.wallpaperBottomColor.toInt()),
                        )
                    )
                )
                .padding(14.dp),
        ) {
            Surface(
                color = Color(appearance.aiBubbleColor.toInt()),
                shape = RoundedCornerShape(5.dp, 17.dp, 17.dp, 17.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth(0.74f),
            ) {
                Text(
                    "This preview updates with your Night appearance.",
                    color = TextMain,
                    fontSize = (14f * appearance.messageFontScale).sp,
                    lineHeight = (19f * appearance.messageFontScale).sp,
                    fontFamily = family,
                    modifier = Modifier.padding(11.dp),
                )
            }

            Surface(
                color = Color(appearance.userBubbleColor.toInt()),
                shape = RoundedCornerShape(17.dp, 5.dp, 17.dp, 17.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxWidth(0.66f),
            ) {
                Text(
                    "Looks good ✨",
                    color = TextMain,
                    fontSize = (14f * appearance.messageFontScale).sp,
                    fontFamily = family,
                    modifier = Modifier.padding(11.dp),
                )
            }
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
    accent: Color,
    onSelected: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
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
                    border =
                        if (selected == choice.argb) {
                            BorderStroke(3.dp, TextMain)
                        } else {
                            null
                        },
                    modifier = Modifier.size(if (selected == choice.argb) 42.dp else 36.dp),
                ) {
                    if (selected == choice.argb) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Check,
                                "Selected " + choice.name,
                                tint =
                                    if (choice.name in setOf("White", "Yellow")) {
                                        Color.Black
                                    } else {
                                        Color.White
                                    },
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                Text(
                    choice.name,
                    color = if (selected == choice.argb) accent else TextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
    }
}
