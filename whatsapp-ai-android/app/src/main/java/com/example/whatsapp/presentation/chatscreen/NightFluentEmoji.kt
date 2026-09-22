package com.example.whatsapp.presentation.chatscreen

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder

/**
 * One Fluent Emoji asset registry for all Night surfaces.
 *
 * Any emoji with a bundled Microsoft Fluent Emoji asset is rendered through
 * this path everywhere. Unknown emoji deliberately falls back to Unicode until
 * its Fluent asset is bundled, rather than each screen inventing its own map.
 */
object NightFluentEmoji {
    private val assets =
        linkedMapOf(
            "😀" to "grinning.svg",
            "😂" to "joy.svg",
            "🥹" to "holding_tears.svg",
            "😍" to "heart_eyes.svg",
            "😭" to "crying.svg",
            "😎" to "sunglasses.svg",
            "👍" to "thumbs_up.svg",
            "❤️" to "red_heart.svg",
            "🙏" to "folded_hands.svg",
            "🔥" to "fire.svg",
        )

    val mappedEmoji: List<String>
        get() = assets.keys.sortedByDescending(String::length)

    fun assetUri(emoji: String): String? =
        assets[emoji]?.let { "file:///android_asset/fluent_emoji/" + it }

    fun contains(emoji: String): Boolean = emoji in assets
}

@Composable
fun NightFluentEmojiGlyph(
    emoji: String,
    size: Dp,
    fallbackFontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val asset = NightFluentEmoji.assetUri(emoji)
    val loader = rememberNightFluentEmojiLoader(context)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        if (asset != null) {
            AsyncImage(
                model = asset,
                imageLoader = loader,
                contentDescription = emoji,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = emoji,
                fontSize = fallbackFontSize,
            )
        }
    }
}

@Composable
internal fun rememberNightFluentEmojiLoader(
    context: Context,
): ImageLoader =
    remember(context.applicationContext) {
        ImageLoader.Builder(context.applicationContext)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }
