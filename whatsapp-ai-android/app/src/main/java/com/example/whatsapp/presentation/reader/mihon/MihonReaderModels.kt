package com.example.whatsapp.presentation.reader.mihon

import android.content.pm.ActivityInfo
import androidx.annotation.DrawableRes
import com.example.whatsapp.R
import org.json.JSONArray
import org.json.JSONObject

/*
 * Adapted from Mihon reader sources.
 * Upstream: https://github.com/mihonapp/mihon
 * Revision: 424bbc53b85c19acd3c3b7c03ec6f73f516f25bc
 * License: Apache-2.0. See third_party/mihon/LICENSE.
 * Modified for Night to accept extension-provided page URLs and headers.
 */
enum class MihonReadingMode(
    val label: String,
    @DrawableRes val iconRes: Int,
) {
    LEFT_TO_RIGHT("Left to right", R.drawable.ic_reader_ltr_24dp),
    RIGHT_TO_LEFT("Right to left", R.drawable.ic_reader_rtl_24dp),
    VERTICAL("Vertical", R.drawable.ic_reader_vertical_24dp),
    WEBTOON("Webtoon", R.drawable.ic_reader_webtoon_24dp),
    CONTINUOUS_VERTICAL("Continuous vertical", R.drawable.ic_reader_continuous_vertical_24dp),
}

enum class MihonReaderOrientation(
    val label: String,
    val activityInfo: Int,
) {
    FREE("Free", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
    PORTRAIT("Portrait", ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT),
    LANDSCAPE("Landscape", ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE),
    LOCKED_PORTRAIT("Locked portrait", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
    LOCKED_LANDSCAPE("Locked landscape", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
    REVERSE_PORTRAIT("Reverse portrait", ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT),
}

data class MihonPageSpec(
    val index: Int,
    val source: String,
    val headers: Map<String, String> = emptyMap(),
)

internal fun encodeMihonPages(pages: List<MihonPageSpec>): String =
    JSONArray().apply {
        pages.forEach { page ->
            put(
                JSONObject()
                    .put("index", page.index)
                    .put("source", page.source)
                    .put(
                        "headers",
                        JSONObject().apply {
                            page.headers.forEach { (name, value) -> put(name, value) }
                        },
                    ),
            )
        }
    }.toString()

internal fun decodeMihonPages(raw: String?): List<MihonPageSpec> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (position in 0 until array.length()) {
                val item = array.getJSONObject(position)
                val headerObject = item.optJSONObject("headers")
                val headers = buildMap {
                    headerObject?.keys()?.forEach { key ->
                        put(key, headerObject.optString(key))
                    }
                }
                add(
                    MihonPageSpec(
                        index = item.optInt("index", position),
                        source = item.getString("source"),
                        headers = headers,
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())
}
