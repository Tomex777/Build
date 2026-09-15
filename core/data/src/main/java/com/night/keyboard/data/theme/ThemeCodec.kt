package com.night.keyboard.data.theme

import com.night.keyboard.model.KeyStyleOverride
import com.night.keyboard.model.ThemeSnapshot
import org.json.JSONArray
import org.json.JSONObject

object ThemeCodec {
    fun encode(theme: ThemeSnapshot): String {
        val root = JSONObject()
            .put("id", theme.id).put("name", theme.name).put("backgroundArgb", theme.backgroundArgb)
            .put("keyFillArgb", theme.keyFillArgb).put("keyLabelArgb", theme.keyLabelArgb)
            .put("secondaryLabelArgb", theme.secondaryLabelArgb).put("accentArgb", theme.accentArgb)
            .put("borderArgb", theme.borderArgb).put("borderWidthDp", theme.borderWidthDp.toDouble())
            .put("cornerRadiusDp", theme.cornerRadiusDp.toDouble()).put("labelSizeSp", theme.labelSizeSp.toDouble())
            .put("keyHeightDp", theme.keyHeightDp.toDouble()).put("horizontalGapDp", theme.horizontalGapDp.toDouble())
            .put("verticalGapDp", theme.verticalGapDp.toDouble())
            .put("borderEnabled", theme.borderEnabled).put("secondaryCharactersVisible", theme.secondaryCharactersVisible)
        val overrides = JSONArray()
        theme.overrides.forEach { (keyId, value) ->
            overrides.put(JSONObject().put("keyId", keyId)
                .putOpt("fillArgb", value.fillArgb).putOpt("labelArgb", value.labelArgb).putOpt("borderArgb", value.borderArgb)
                .putOpt("borderWidthDp", value.borderWidthDp).putOpt("cornerRadiusDp", value.cornerRadiusDp)
                .putOpt("labelSizeSp", value.labelSizeSp).putOpt("widthScale", value.widthScale).putOpt("heightDp", value.heightDp)
                .putOpt("bold", value.bold).putOpt("italic", value.italic)
                .putOpt("fillAlpha", value.fillAlpha).putOpt("borderEnabled", value.borderEnabled).putOpt("invisibleFill", value.invisibleFill))
        }
        return root.put("overrides", overrides).toString()
    }

    fun decode(json: String): ThemeSnapshot = runCatching {
        val root = JSONObject(json)
        val map = linkedMapOf<String, KeyStyleOverride>()
        val overrides = root.optJSONArray("overrides") ?: JSONArray()
        for (i in 0 until overrides.length()) {
            val o = overrides.getJSONObject(i)
            map[o.getString("keyId")] = KeyStyleOverride(
                fillArgb = o.optLongOrNull("fillArgb"), labelArgb = o.optLongOrNull("labelArgb"), borderArgb = o.optLongOrNull("borderArgb"),
                borderWidthDp = o.optDoubleOrNull("borderWidthDp")?.toFloat(), cornerRadiusDp = o.optDoubleOrNull("cornerRadiusDp")?.toFloat(),
                labelSizeSp = o.optDoubleOrNull("labelSizeSp")?.toFloat(), widthScale = o.optDoubleOrNull("widthScale")?.toFloat(), heightDp = o.optDoubleOrNull("heightDp")?.toFloat(),
                bold = o.optBooleanOrNull("bold"), italic = o.optBooleanOrNull("italic"),
                fillAlpha = o.optDoubleOrNull("fillAlpha")?.toFloat(), borderEnabled = o.optBooleanOrNull("borderEnabled"), invisibleFill = o.optBooleanOrNull("invisibleFill"),
            )
        }
        ThemeSnapshot(
            id = root.optLong("id", 0), name = root.optString("name", "Graphite"), backgroundArgb = root.optLong("backgroundArgb", 0xFF050607),
            keyFillArgb = root.optLong("keyFillArgb", 0), keyLabelArgb = root.optLong("keyLabelArgb", 0xFFF6F6F6), secondaryLabelArgb = root.optLong("secondaryLabelArgb", 0xFF9AA2AA),
            accentArgb = root.optLong("accentArgb", 0xFF6EA8FF), borderArgb = root.optLong("borderArgb", 0), borderWidthDp = root.optDouble("borderWidthDp", 0.0).toFloat(),
            cornerRadiusDp = root.optDouble("cornerRadiusDp", 8.0).toFloat(), labelSizeSp = root.optDouble("labelSizeSp", 19.0).toFloat(),
            keyHeightDp = root.optDouble("keyHeightDp", 50.0).toFloat(), horizontalGapDp = root.optDouble("horizontalGapDp", 1.0).toFloat(), verticalGapDp = root.optDouble("verticalGapDp", 0.0).toFloat(),
            borderEnabled = root.optBoolean("borderEnabled", false), secondaryCharactersVisible = root.optBoolean("secondaryCharactersVisible", true), overrides = map,
        )
    }.getOrElse { ThemeSnapshot() }
}

private fun JSONObject.optLongOrNull(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null
private fun JSONObject.optDoubleOrNull(key: String): Double? = if (has(key) && !isNull(key)) getDouble(key) else null
private fun JSONObject.optBooleanOrNull(key: String): Boolean? = if (has(key) && !isNull(key)) getBoolean(key) else null
