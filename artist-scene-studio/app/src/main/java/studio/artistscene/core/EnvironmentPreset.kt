package studio.artistscene.core

import kotlinx.serialization.Serializable

/**
 * Offline-first artist-reference backdrops. These contain color design data only:
 * no copied Magic Poser assets, internet dependency, or external texture licensing.
 * Use image-based lighting / CC0 HDRI as a separate renderer integration.
 */
@Serializable
enum class EnvironmentPreset(
    val title: String,
    val groundHex: String,
    val roughness: Float,
    val zenithRgb: Int,
    val horizonRgb: Int,
    val lowerSkyRgb: Int,
    val cloudAmount: Float,
    val sunStrength: Float,
) {
    STUDIO_GREY("Photo Studio", "#65717E", 0.93f, 0x939FAC, 0xC6CBD1, 0x848D98, 0f, 0f),
    DAYLIGHT("Outdoor Daylight", "#65717B", 0.86f, 0x377CB8, 0xC2D9E9, 0xA9B7BD, 0.33f, 0.75f),
    SUNSET("Warm Sunset", "#626876", 0.91f, 0x4F5A93, 0xF4C49A, 0x968079, 0.21f, 0.95f),
    OVERCAST("Soft Overcast", "#68737C", 0.98f, 0x7E98AA, 0xC4D0D7, 0xA2ADB6, 0.52f, 0.12f),
    CYC("Cyclorama", "#67747F", 0.94f, 0xB2BEC8, 0xC8D0D5, 0xA1AEB9, 0f, 0f),
    REFERENCE("Plain Reference", "#646E79", 1f, 0x34465B, 0x485769, 0x303C4D, 0f, 0f),
}
