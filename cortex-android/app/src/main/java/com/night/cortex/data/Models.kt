package com.night.cortex.data

import org.json.JSONArray
import org.json.JSONObject

data class CoreConnection(
    val baseUrl: String = "",
    val token: String = "",
) {
    val configured: Boolean get() = baseUrl.isNotBlank() && token.isNotBlank()
}

data class CoreHealth(
    val online: Boolean,
    val service: String = "night-core",
    val detail: String? = null,
)

data class CoreSnapshot(
    val service: String,
    val sessionCount: Int,
    val connectedSessions: Int,
    val allowedChats: Int,
    val aiEnabled: Boolean?,
)

data class InboxChat(
    val jid: String,
    val name: String?,
    val lastMessageAt: Long,
    val unreadCount: Int,
    val preview: String?,
    val previewType: String?,
    val previewFromMe: Boolean,
) {
    val title: String get() = name?.takeIf { it.isNotBlank() } ?: jid.substringBefore('@')
    val isGroup: Boolean get() = jid.endsWith("@g.us")
}

data class InboxMessage(
    val id: String,
    val chatJid: String,
    val pushName: String?,
    val fromMe: Boolean,
    val timestamp: Long,
    val type: String,
    val text: String?,
    val viewOnce: Boolean,
    val fileName: String?,
    val seconds: Int?,
)

fun JSONObject.toCoreSnapshot(): CoreSnapshot {
    val sessions = opt("sessions")
    var total = 0
    var connected = 0

    when (sessions) {
        is JSONObject -> {
            total = sessions.length()
            sessions.keys().forEach { key ->
                val node = sessions.optJSONObject(key)
                if (
                    node?.optBoolean("connected", false) == true ||
                    node?.optString("state") == "connected" ||
                    node?.optString("status") == "open"
                ) connected++
            }
        }
        is JSONArray -> {
            total = sessions.length()
            for (i in 0 until sessions.length()) {
                val node = sessions.optJSONObject(i)
                if (
                    node?.optBoolean("connected", false) == true ||
                    node?.optString("state") == "connected" ||
                    node?.optString("status") == "open"
                ) connected++
            }
        }
    }

    val allowed = optJSONArray("allowedChats")?.length() ?: 0
    val config = optJSONObject("config")
    val aiRaw = config?.opt("AI_ENABLED") ?: config?.opt("ai.enabled")
    val ai = when (aiRaw) {
        is Boolean -> aiRaw
        is String -> aiRaw.equals("true", true) || aiRaw.equals("on", true)
        else -> null
    }

    return CoreSnapshot(
        service = optString("service", "night-core"),
        sessionCount = total,
        connectedSessions = connected,
        allowedChats = allowed,
        aiEnabled = ai,
    )
}

fun JSONArray.toInboxChats(): List<InboxChat> = buildList {
    for (i in 0 until length()) {
        val o = optJSONObject(i) ?: continue
        add(
            InboxChat(
                jid = o.optString("jid"),
                name = o.optString("name").takeIf { it.isNotBlank() && it != "null" },
                lastMessageAt = o.optLong("last_message_at", 0L),
                unreadCount = o.optInt("unread_count", 0),
                preview = o.optString("preview").takeIf { it.isNotBlank() && it != "null" },
                previewType = o.optString("preview_type").takeIf { it.isNotBlank() && it != "null" },
                previewFromMe = o.optInt("preview_from_me", 0) == 1,
            )
        )
    }
}

fun JSONArray.toInboxMessages(): List<InboxMessage> = buildList {
    for (i in 0 until length()) {
        val o = optJSONObject(i) ?: continue
        add(
            InboxMessage(
                id = o.optString("id"),
                chatJid = o.optString("chatJid"),
                pushName = o.optString("pushName").takeIf { it.isNotBlank() && it != "null" },
                fromMe = o.optBoolean("fromMe", false),
                timestamp = o.optLong("timestamp", 0L),
                type = o.optString("type", "unknown"),
                text = o.optString("text").takeIf { it.isNotBlank() && it != "null" },
                viewOnce = o.optBoolean("viewOnce", false),
                fileName = o.optString("fileName").takeIf { it.isNotBlank() && it != "null" },
                seconds = if (o.has("seconds") && !o.isNull("seconds")) o.optInt("seconds") else null,
            )
        )
    }
}
