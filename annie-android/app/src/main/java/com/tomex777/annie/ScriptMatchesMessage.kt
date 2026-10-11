package com.tomex777.annie

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

private val MatchesBubble = Color(0xFF13243A)
private val MatchesSurface = Color(0xFF10263D)
private val MatchesBorder = Color(0xFF294562)
private val MatchesText = Color(0xFFEEF5FF)
private val MatchesMuted = Color(0xFF9CB2CC)
private val MatchesBlue = Color(0xFF42B9F5)

internal data class NativeMatchItem(
    val index: Int,
    val id: String,
    val title: String,
    val subtitle: String,
    val sourceName: String,
    val thumbnail: String?,
    val relevance: String?,
    val action: String,
    val payloadJson: String,
)

internal object ScriptMatchesParser {
    const val MAX_ITEMS = 40

    fun parse(data: JSONObject): List<NativeMatchItem> {
        val rows = data.optJSONArray("items") ?: data.optJSONArray("matches") ?: JSONArray()
        return buildList {
            for (index in 0 until minOf(rows.length(), MAX_ITEMS)) {
                val row = rows.optJSONObject(index) ?: continue
                val title = row.optString("title").trim()
                if (title.isBlank()) continue
                val id = row.optString("id").ifBlank { index.toString() }
                val sourceName = row.optString("sourceName")
                    .ifBlank { row.optString("source") }
                    .ifBlank { row.optString("provider") }
                val subtitle = row.optString("subtitle").ifBlank {
                    buildList {
                        row.optInt("year").takeIf { it > 0 }?.let { add(it.toString()) }
                        row.optString("mediaType").takeIf(String::isNotBlank)?.let { add(it.uppercase()) }
                    }.joinToString(" · ")
                }
                val relevance = formatRelevance(row.opt("relevance") ?: row.opt("confidence"))
                val action = row.optString("action").ifBlank { "select" }
                val payload = row.opt("payload")
                val payloadJson = when (payload) {
                    is JSONObject, is JSONArray -> payload.toString()
                    null, JSONObject.NULL -> JSONObject()
                        .put("id", id)
                        .put("title", title)
                        .put("sourceName", sourceName)
                        .put("index", index)
                        .toString()
                    else -> JSONObject()
                        .put("id", id)
                        .put("title", title)
                        .put("sourceName", sourceName)
                        .put("value", payload)
                        .put("index", index)
                        .toString()
                }
                add(
                    NativeMatchItem(
                        index = index,
                        id = id,
                        title = title,
                        subtitle = subtitle,
                        sourceName = sourceName,
                        thumbnail = row.optString("thumbnail").ifBlank { row.optString("image") }
                            .takeIf(String::isNotBlank),
                        relevance = relevance,
                        action = action,
                        payloadJson = payloadJson,
                    )
                )
            }
        }
    }

    private fun formatRelevance(value: Any?): String? = when (value) {
        is Number -> {
            val score = value.toDouble()
            if (!score.isFinite() || score < 0.0) null else {
                val percent = if (score <= 1.0) score * 100.0 else score
                if (percent > 100.0) null else "${percent.roundToInt()}% match"
            }
        }
        is String -> value.trim().takeIf(String::isNotEmpty)?.let { label ->
            val numeric = label.removeSuffix("%").trim().toDoubleOrNull()
            if (numeric == null || !numeric.isFinite() || numeric !in 0.0..100.0) label
            else {
                val percent = if (numeric <= 1.0) numeric * 100.0 else numeric
                "${percent.roundToInt()}% match"
            }
        }
        else -> null
    }
}

@Composable
internal fun ScriptMatchesMessage(
    data: JSONObject,
    scriptId: String,
    onAction: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val items = remember(data.toString()) { ScriptMatchesParser.parse(data) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp))
            .background(MatchesBubble).padding(12.dp).testTag("script_matches_message"),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            data.optString("title").ifBlank { "Possible matches" },
            color = MatchesText,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        data.optString("subtitle").takeIf(String::isNotBlank)?.let {
            Text(it, color = MatchesMuted, fontSize = 11.sp)
        }
        if (items.isEmpty()) {
            Text("No matches were returned.", color = MatchesMuted, fontSize = 12.sp)
        } else {
            items.forEach { item ->
                Surface(
                    color = MatchesSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MatchesBorder),
                    modifier = Modifier.fillMaxWidth().clickable { onAction(item.action, item.payloadJson) }
                        .testTag("script_match_" + item.index),
                ) {
                    Row(
                        Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val model = item.thumbnail?.let { resolvePackageResourceUri(context, scriptId, it) }
                        if (model != null) {
                            AsyncImage(
                                model = model,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(54.dp).aspectRatio(1f).clip(RoundedCornerShape(11.dp)),
                            )
                        } else {
                            Box(
                                Modifier.size(54.dp).background(Color(0xFF183553), RoundedCornerShape(11.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(item.title.take(1).uppercase(), color = MatchesBlue, fontWeight = FontWeight.Bold)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.title,
                                color = MatchesText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (item.subtitle.isNotBlank()) {
                                Text(
                                    item.subtitle,
                                    color = MatchesMuted,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (item.sourceName.isNotBlank()) {
                                Text(
                                    item.sourceName,
                                    color = MatchesBlue,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            item.relevance?.let {
                                Text(
                                    it,
                                    color = MatchesMuted,
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    modifier = Modifier.testTag("script_match_relevance_${item.index}"),
                                )
                            }
                            Icon(
                                AnnieIcons.ChevronRight,
                                contentDescription = null,
                                tint = MatchesMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
