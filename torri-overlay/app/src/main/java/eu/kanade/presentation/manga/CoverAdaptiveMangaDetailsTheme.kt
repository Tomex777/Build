package eu.kanade.presentation.manga

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.domain.manga.model.Manga
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Applies a cover-derived color treatment only to the current manga details hierarchy.
 *
 * The global Torri theme remains authoritative everywhere else. The palette starts from
 * the current theme, loads asynchronously, and animates into a deliberately restrained
 * cover-derived variant so chapter management remains readable and familiar.
 */
@Composable
internal fun CoverAdaptiveMangaDetailsTheme(
    manga: Manga,
    content: @Composable () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val base = MaterialTheme.colorScheme
    val cacheKey = remember(manga.id, manga.coverLastModified, manga.thumbnailUrl) {
        CoverPaletteCache.keyFor(manga)
    }

    var seed by remember(cacheKey) {
        mutableStateOf(CoverPaletteCache.get(context, cacheKey))
    }

    LaunchedEffect(cacheKey) {
        if (seed == null) {
            seed = CoverPaletteCache.load(context, manga, cacheKey)
        }
    }

    val target = remember(base, seed) {
        seed?.let { deriveScheme(base, it) } ?: base
    }

    @Composable
    @Composable
    fun animated(targetColor: Color, label: String): Color {
        val value by animateColorAsState(
            targetValue = targetColor,
            animationSpec = tween(durationMillis = 420),
            label = label,
        )
        return value
    }

    val animatedScheme = base.copy(
        primary = animated(target.primary, "Torri details primary"),
        onPrimary = animated(target.onPrimary, "Torri details onPrimary"),
        primaryContainer = animated(target.primaryContainer, "Torri details primaryContainer"),
        onPrimaryContainer = animated(target.onPrimaryContainer, "Torri details onPrimaryContainer"),
        secondary = animated(target.secondary, "Torri details secondary"),
        onSecondary = animated(target.onSecondary, "Torri details onSecondary"),
        secondaryContainer = animated(target.secondaryContainer, "Torri details secondaryContainer"),
        onSecondaryContainer = animated(target.onSecondaryContainer, "Torri details onSecondaryContainer"),
        background = animated(target.background, "Torri details background"),
        onBackground = animated(target.onBackground, "Torri details onBackground"),
        surface = animated(target.surface, "Torri details surface"),
        onSurface = animated(target.onSurface, "Torri details onSurface"),
        surfaceVariant = animated(target.surfaceVariant, "Torri details surfaceVariant"),
        onSurfaceVariant = animated(target.onSurfaceVariant, "Torri details onSurfaceVariant"),
        surfaceContainerLowest = animated(target.surfaceContainerLowest, "Torri details surfaceContainerLowest"),
        surfaceContainerLow = animated(target.surfaceContainerLow, "Torri details surfaceContainerLow"),
        surfaceContainer = animated(target.surfaceContainer, "Torri details surfaceContainer"),
        surfaceContainerHigh = animated(target.surfaceContainerHigh, "Torri details surfaceContainerHigh"),
        surfaceContainerHighest = animated(target.surfaceContainerHighest, "Torri details surfaceContainerHighest"),
        outline = animated(target.outline, "Torri details outline"),
        outlineVariant = animated(target.outlineVariant, "Torri details outlineVariant"),
    )

    MaterialTheme(
        colorScheme = animatedScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}

private data class CoverPaletteSeed(
    val primary: Int,
    val secondary: Int,
)

private object CoverPaletteCache {
    private const val PREFS_NAME = "torri_cover_palette_cache_v1"
    private const val MAX_DISK_ENTRIES = 256
    private val memory = LruCache<String, CoverPaletteSeed>(64)

    fun keyFor(manga: Manga): String {
        return listOf(
            manga.id,
            manga.source,
            manga.coverLastModified,
            manga.thumbnailUrl.orEmpty().hashCode(),
        ).joinToString(":")
    }

    fun get(context: Context, key: String): CoverPaletteSeed? {
        memory.get(key)?.let { return it }

        val encoded = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(key, null)
            ?: return null

        return decode(encoded)?.also { memory.put(key, it) }
    }

    suspend fun load(context: Context, manga: Manga, key: String): CoverPaletteSeed? {
        get(context, key)?.let { return it }

        val result = withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(context)
                .data(manga)
                .precision(Precision.EXACT)
                .size(72, 108)
                .scale(Scale.FILL)
                .build()

            val bitmap = context.imageLoader.execute(request)
                .image
                ?.asDrawable(context.resources)
                ?.toBitmap()

            bitmap?.let(::extractSeed)
        }

        if (result != null) {
            memory.put(key, result)
            persist(context, key, result)
        }
        return result
    }

    private fun persist(context: Context, key: String, seed: CoverPaletteSeed) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit().putString(key, encode(seed))

        // SharedPreferences is intentionally tiny here. Trim old arbitrary entries rather
        // than allowing hundreds of covers to turn this into an unbounded disk cache.
        if (prefs.all.size >= MAX_DISK_ENTRIES && !prefs.contains(key)) {
            prefs.all.keys.take(48).forEach(editor::remove)
        }
        editor.apply()
    }

    private fun encode(seed: CoverPaletteSeed): String {
        return "${seed.primary.toUInt().toString(16)},${seed.secondary.toUInt().toString(16)}"
    }

    private fun decode(value: String): CoverPaletteSeed? {
        val parts = value.split(',')
        if (parts.size != 2) return null
        return runCatching {
            CoverPaletteSeed(
                primary = parts[0].toUInt(16).toInt(),
                secondary = parts[1].toUInt(16).toInt(),
            )
        }.getOrNull()
    }
}

private data class HueBucket(
    var count: Int = 0,
    var saturationTotal: Float = 0f,
    var lightnessTotal: Float = 0f,
) {
    val saturation: Float get() = if (count == 0) 0f else saturationTotal / count
    val lightness: Float get() = if (count == 0) 0f else lightnessTotal / count
}

private fun extractSeed(bitmap: Bitmap): CoverPaletteSeed? {
    if (bitmap.width <= 0 || bitmap.height <= 0) return null

    val buckets = Array(24) { HueBucket() }
    val hsl = FloatArray(3)
    var validPixels = 0
    var saturationTotal = 0f
    var luminanceTotal = 0.0
    var minLuminance = 1.0
    var maxLuminance = 0.0

    val stepX = max(1, bitmap.width / 36)
    val stepY = max(1, bitmap.height / 54)

    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val color = bitmap.getPixel(x, y)
            if (android.graphics.Color.alpha(color) >= 180) {
                ColorUtils.colorToHSL(color, hsl)
                val saturation = hsl[1]
                val lightness = hsl[2]
                val luminance = ColorUtils.calculateLuminance(color)

                validPixels += 1
                saturationTotal += saturation
                luminanceTotal += luminance
                minLuminance = min(minLuminance, luminance)
                maxLuminance = max(maxLuminance, luminance)

                // Ignore near-black/white pixels for hue selection. They still contribute
                // to low-information detection below.
                if (saturation >= 0.10f && lightness in 0.06f..0.94f) {
                    val index = ((hsl[0] / 360f) * buckets.size)
                        .toInt()
                        .coerceIn(0, buckets.lastIndex)
                    buckets[index].apply {
                        count += 1
                        saturationTotal += saturation
                        lightnessTotal += lightness
                    }
                }
            }
            x += stepX
        }
        y += stepY
    }

    if (validPixels < 48) return null

    val averageSaturation = saturationTotal / validPixels
    val averageLuminance = luminanceTotal / validPixels
    val luminanceRange = maxLuminance - minLuminance

    // Mostly black/white, muddy, or nearly monochrome covers should inherit Torri's
    // normal theme rather than manufacturing a weak or ugly tint.
    if (
        averageSaturation < 0.075f ||
        (averageSaturation < 0.14f && luminanceRange < 0.20) ||
        (averageSaturation < 0.18f && (averageLuminance < 0.045 || averageLuminance > 0.92))
    ) {
        return null
    }

    val populated = buckets.withIndex().filter { it.value.count > 0 }
    if (populated.isEmpty()) return null

    val primaryEntry = populated.maxByOrNull { (_, bucket) ->
        bucket.count * (0.55f + bucket.saturation) * (1f - abs(bucket.lightness - 0.52f) * 0.55f)
    } ?: return null

    val primaryHue = bucketHue(primaryEntry.index, buckets.size)
    val primary = tonedColor(
        hue = primaryHue,
        saturation = primaryEntry.value.saturation.coerceIn(0.40f, 0.78f),
        lightness = 0.52f,
    )

    val secondaryEntry = populated
        .filter { (index, _) -> hueDistance(primaryHue, bucketHue(index, buckets.size)) >= 42f }
        .maxByOrNull { (_, bucket) ->
            bucket.count * (0.45f + bucket.saturation) * (1f - abs(bucket.lightness - 0.52f) * 0.45f)
        }

    val secondaryHue = secondaryEntry
        ?.let { bucketHue(it.index, buckets.size) }
        ?: ((primaryHue + 32f) % 360f)

    val secondarySaturation = secondaryEntry
        ?.value
        ?.saturation
        ?.coerceIn(0.30f, 0.65f)
        ?: 0.42f

    val secondary = tonedColor(
        hue = secondaryHue,
        saturation = secondarySaturation,
        lightness = 0.52f,
    )

    return CoverPaletteSeed(primary = primary, secondary = secondary)
}

private fun deriveScheme(
    base: androidx.compose.material3.ColorScheme,
    seed: CoverPaletteSeed,
): androidx.compose.material3.ColorScheme {
    val isDark = base.background.luminance() < 0.45f
    val primarySeed = seed.primary
    val secondarySeed = seed.secondary

    val background = toneForBackground(primarySeed, isDark)
    val surface = blend(background, base.surface.toArgb(), if (isDark) 0.26f else 0.34f)
    val surfaceVariant = blend(background, secondarySeed, if (isDark) 0.18f else 0.11f)

    val primary = toneForAccent(primarySeed, isDark)
    val secondary = toneForSecondary(secondarySeed, isDark)
    val primaryContainer = blend(background, primary, if (isDark) 0.42f else 0.22f)
    val secondaryContainer = blend(background, secondary, if (isDark) 0.34f else 0.18f)

    val onBackground = contrastNeutral(background, base.onBackground.toArgb())
    val onSurface = contrastNeutral(surface, base.onSurface.toArgb())
    val onSurfaceVariant = contrastNeutral(surfaceVariant, base.onSurfaceVariant.toArgb())

    return base.copy(
        primary = Color(primary),
        onPrimary = Color(bestForeground(primary)),
        primaryContainer = Color(primaryContainer),
        onPrimaryContainer = Color(bestForeground(primaryContainer)),
        secondary = Color(secondary),
        onSecondary = Color(bestForeground(secondary)),
        secondaryContainer = Color(secondaryContainer),
        onSecondaryContainer = Color(bestForeground(secondaryContainer)),
        background = Color(background),
        onBackground = Color(onBackground),
        surface = Color(surface),
        onSurface = Color(onSurface),
        surfaceVariant = Color(surfaceVariant),
        onSurfaceVariant = Color(onSurfaceVariant),
        surfaceContainerLowest = Color(blend(background, base.surfaceContainerLowest.toArgb(), 0.20f)),
        surfaceContainerLow = Color(blend(background, base.surfaceContainerLow.toArgb(), 0.26f)),
        surfaceContainer = Color(blend(background, base.surfaceContainer.toArgb(), 0.32f)),
        surfaceContainerHigh = Color(blend(background, base.surfaceContainerHigh.toArgb(), 0.38f)),
        surfaceContainerHighest = Color(blend(background, base.surfaceContainerHighest.toArgb(), 0.44f)),
        outline = Color(blend(base.outline.toArgb(), secondary, 0.16f)),
        outlineVariant = Color(blend(base.outlineVariant.toArgb(), secondary, 0.12f)),
    )
}

private fun toneForBackground(seed: Int, dark: Boolean): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed, hsl)
    hsl[1] = hsl[1].coerceAtMost(if (dark) 0.30f else 0.20f)
    hsl[2] = if (dark) 0.105f else 0.955f
    return ColorUtils.HSLToColor(hsl)
}

private fun toneForAccent(seed: Int, dark: Boolean): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed, hsl)
    hsl[1] = hsl[1].coerceIn(0.42f, 0.76f)
    hsl[2] = if (dark) 0.66f else 0.40f
    return ColorUtils.HSLToColor(hsl)
}

private fun toneForSecondary(seed: Int, dark: Boolean): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed, hsl)
    hsl[1] = hsl[1].coerceIn(0.28f, 0.62f)
    hsl[2] = if (dark) 0.70f else 0.38f
    return ColorUtils.HSLToColor(hsl)
}

private fun tonedColor(hue: Float, saturation: Float, lightness: Float): Int {
    return ColorUtils.HSLToColor(floatArrayOf(hue, saturation, lightness))
}

private fun bucketHue(index: Int, count: Int): Float {
    return ((index + 0.5f) / count) * 360f
}

private fun hueDistance(a: Float, b: Float): Float {
    val difference = abs(a - b)
    return min(difference, 360f - difference)
}

private fun blend(background: Int, foreground: Int, ratio: Float): Int {
    return ColorUtils.blendARGB(background, foreground, ratio.coerceIn(0f, 1f))
}

private fun bestForeground(background: Int): Int {
    val white = android.graphics.Color.WHITE
    val black = android.graphics.Color.BLACK
    return if (ColorUtils.calculateContrast(white, background) >= ColorUtils.calculateContrast(black, background)) {
        white
    } else {
        black
    }
}

private fun contrastNeutral(background: Int, preferred: Int): Int {
    if (ColorUtils.calculateContrast(preferred, background) >= 4.5) return preferred
    return bestForeground(background)
}
