package eu.kanade.domain.ui.model

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR

enum class AppTheme(val titleRes: StringResource?) {
    DEFAULT(MR.strings.label_default),
    MONET(MR.strings.theme_monet),
    CATPPUCCIN(MR.strings.theme_catppuccin),
    GREEN_APPLE(MR.strings.theme_greenapple),
    LAVENDER(MR.strings.theme_lavender),
    MIDNIGHT_DUSK(MR.strings.theme_midnightdusk),
    NORD(MR.strings.theme_nord),
    STRAWBERRY_DAIQUIRI(MR.strings.theme_strawberrydaiquiri),
    TAKO(MR.strings.theme_tako),
    TEALTURQUOISE(MR.strings.theme_tealturquoise),
    TIDAL_WAVE(MR.strings.theme_tidalwave),
    YINYANG(MR.strings.theme_yinyang),
    YOTSUBA(MR.strings.theme_yotsuba),
    TOKYONIGHT(MR.strings.theme_tokyonight),
    MONOCHROME(MR.strings.theme_monochrome),
    NIGHT(MR.strings.theme_night),
    OBSIDIAN_GOLD(MR.strings.theme_obsidian_gold),
    PARCHMENT(MR.strings.theme_parchment),
    EMBER(MR.strings.theme_ember),
    DRACULA(MR.strings.theme_dracula),
    ROSE_PINE(MR.strings.theme_rose_pine),
    SOLARIZED(MR.strings.theme_solarized),
    CUSTOM(MR.strings.theme_custom),

    // Deprecated
    DARK_BLUE(null),
    HOT_PINK(null),
    BLUE(null),
}
