package com.night.keyboard.model

enum class KeyboardLayer { LETTERS, SYMBOLS, SYMBOLS_MORE }
enum class ShiftState { OFF, ONCE, LOCKED }
enum class SpecialKey { SHIFT, BACKSPACE, ENTER, SPACE, EMOJI, NUMBERS, LETTERS, MORE_SYMBOLS, LESS_SYMBOLS, COMMA, PERIOD, VOICE }

data class KeySpec(val id: String, val label: String, val output: String? = null, val secondary: String? = null, val special: SpecialKey? = null, val weight: Float = 1f)

data class KeyStyleOverride(
    val fillArgb: Long? = null,
    val labelArgb: Long? = null,
    val borderArgb: Long? = null,
    val borderWidthDp: Float? = null,
    val cornerRadiusDp: Float? = null,
    val labelSizeSp: Float? = null,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val fillAlpha: Float? = null,
    val borderEnabled: Boolean? = null,
    val invisibleFill: Boolean? = null,
)

data class ThemeSnapshot(
    val id: Long = 0,
    val name: String = "Graphite",
    val backgroundArgb: Long = 0xFF050607,
    val keyFillArgb: Long = 0x00000000,
    val keyLabelArgb: Long = 0xFFF6F6F6,
    val secondaryLabelArgb: Long = 0xFF9AA2AA,
    val accentArgb: Long = 0xFF6EA8FF,
    val borderArgb: Long = 0x00000000,
    val borderWidthDp: Float = 0f,
    val cornerRadiusDp: Float = 8f,
    val labelSizeSp: Float = 19f,
    val borderEnabled: Boolean = false,
    val secondaryCharactersVisible: Boolean = true,
    val overrides: Map<String, KeyStyleOverride> = emptyMap(),
)

enum class ClipboardKind { TEXT, LINK, PHONE, ADDRESS }

data class ClipboardItem(val id: Long, val text: String, val kind: ClipboardKind, val createdAt: Long, val expiresAt: Long?, val pinned: Boolean, val orderIndex: Long)

enum class RetentionPreset(val minutes: Long?) {
    ONE_HOUR(60), TWO_HOURS(120), SIX_HOURS(360), TWELVE_HOURS(720), TWENTY_FOUR_HOURS(1440), END_OF_DAY(-2), NEVER(null)
}
