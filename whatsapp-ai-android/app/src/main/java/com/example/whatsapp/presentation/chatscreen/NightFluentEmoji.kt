package com.example.whatsapp.presentation.chatscreen

import android.content.Context
import android.icu.text.BreakIterator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.InlineTextContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.appendInlineContent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    fun assetUri(emoji: String): String? {
        assets[emoji]
            ?.let { return "file:///android_asset/fluent_emoji/" + it }

        if (!isEmojiCluster(emoji)) return null
        val unicodeKey =
            emoji.codePoints()
                .toArray()
                .asSequence()
                // Fluent's unicode-organized asset set uses the semantic
                // sequence; text/emoji presentation selectors are omitted.
                .filterNot { it == 0xFE0E || it == 0xFE0F }
                .joinToString("-") { it.toString(16) }
        if (unicodeKey.isBlank()) return null

        return "https://cdn.jsdelivr.net/gh/shuding/fluentui-emoji-unicode/assets/" +
            unicodeKey +
            "_color.svg"
    }

    fun isEmojiCluster(cluster: String): Boolean =
        cluster.codePoints().anyMatch(::isEmojiCodePoint)

    fun tokenize(text: String): List<Token> {
        if (text.isEmpty()) return emptyList()

        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text)

        val tokens = mutableListOf<Token>()
        var textBuffer = StringBuilder()
        var start = iterator.first()
        var end = iterator.next()

        fun flushText() {
            if (textBuffer.isNotEmpty()) {
                tokens += Token.Text(textBuffer.toString())
                textBuffer = StringBuilder()
            }
        }

        while (end != BreakIterator.DONE) {
            val cluster = text.substring(start, end)
            if (isEmojiCluster(cluster)) {
                flushText()
                tokens += Token.Emoji(cluster)
            } else {
                textBuffer.append(cluster)
            }
            start = end
            end = iterator.next()
        }
        flushText()
        return tokens
    }

    sealed interface Token {
        data class Text(val value: String) : Token
        data class Emoji(val value: String) : Token
    }

    private fun isEmojiCodePoint(codePoint: Int): Boolean =
        codePoint in 0x1F000..0x1FAFF ||
            codePoint in 0x2600..0x27BF ||
            codePoint in 0x2300..0x23FF ||
            codePoint in 0x2B00..0x2BFF ||
            codePoint in 0x1F1E6..0x1F1FF ||
            codePoint == 0x00A9 ||
            codePoint == 0x00AE ||
            codePoint == 0x203C ||
            codePoint == 0x2049 ||
            codePoint == 0x2122 ||
            codePoint == 0x2139 ||
            codePoint == 0x3030 ||
            codePoint == 0x303D ||
            codePoint == 0x3297 ||
            codePoint == 0x3299
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
    var failed by remember(emoji, asset) { mutableStateOf(false) }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        if (asset != null && !failed) {
            AsyncImage(
                model = asset,
                imageLoader = loader,
                contentDescription = emoji,
                modifier = Modifier.fillMaxSize(),
                onError = { failed = true },
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
fun NightFluentEmojiText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    lineHeight: TextUnit = TextUnit.Unspecified,
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val loader = rememberNightFluentEmojiLoader(context)
    val parsed =
        remember(text) {
            val used = linkedSetOf<String>()
            val annotated =
                buildAnnotatedString {
                    NightFluentEmoji.tokenize(text).forEach { token ->
                        when (token) {
                            is NightFluentEmoji.Token.Text ->
                                append(token.value)

                            is NightFluentEmoji.Token.Emoji -> {
                                val inlineId =
                                    "night_fluent_" +
                                        token.value.codePoints()
                                            .toArray()
                                            .joinToString("_")
                                appendInlineContent(inlineId, token.value)
                                used += token.value
                            }
                        }
                    }
                }
            annotated to used.toList()
        }
    val inline =
        buildMap<String, InlineTextContent> {
            parsed.second.forEach { emoji ->
                val asset = NightFluentEmoji.assetUri(emoji) ?: return@forEach
                val inlineId =
                    "night_fluent_" +
                        emoji.codePoints()
                            .toArray()
                            .joinToString("_")
                put(
                    inlineId,
                    InlineTextContent(
                        Placeholder(
                            width = 1.15.em,
                            height = 1.15.em,
                            placeholderVerticalAlign =
                                PlaceholderVerticalAlign.TextCenter,
                        )
                    ) {
                        var failed by remember(emoji, asset) {
                            mutableStateOf(false)
                        }
                        if (!failed) {
                            AsyncImage(
                                model = asset,
                                imageLoader = loader,
                                contentDescription = emoji,
                                modifier = Modifier.fillMaxSize(),
                                onError = { failed = true },
                            )
                        } else {
                            Text(
                                text = emoji,
                                fontSize = fontSize,
                            )
                        }
                    }
                )
            }
        }

    Text(
        text = parsed.first,
        inlineContent = inline,
        color = color,
        fontSize = fontSize,
        modifier = modifier,
        lineHeight = lineHeight,
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = overflow,
    )
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
