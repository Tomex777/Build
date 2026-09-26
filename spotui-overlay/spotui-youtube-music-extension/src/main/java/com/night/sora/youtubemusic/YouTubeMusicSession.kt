package com.night.sora.youtubemusic

import android.content.Context
import android.net.Uri
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB_REMIX
import org.json.JSONObject

/** Keeps YouTube's browser-owned client identifiers aligned with Innertube. */
internal object YouTubeMusicSession {
    private const val PREFS = "sora_youtube_music_session_v1"
    private const val VISITOR_DATA = "visitorData"
    private const val DATA_SYNC_ID = "dataSyncId"
    private const val AUTH_USER = "authUser"

    fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(VISITOR_DATA, null)?.takeIf(String::isNotBlank)?.let { YouTube.visitorData = it }
        prefs.getString(DATA_SYNC_ID, null)?.takeIf(String::isNotBlank)?.let { YouTube.dataSyncId = it }
    }

    fun browserSession(): String {
        val continueUrl = Uri.encode("https://music.youtube.com/")
        val loginUrl = "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true&continue=$continueUrl"
        return JSONObject()
            .put("url", loginUrl)
            .put("title", "YouTube Music sign in")
            .put(
                "headers",
                JSONObject().put("User-Agent", WEB_REMIX.userAgent),
            )
            .put(
                "sessionScripts",
                JSONObject()
                    .put(
                        VISITOR_DATA,
                        "(function(){try{return window.yt&&window.yt.config_?window.yt.config_.VISITOR_DATA:null}catch(e){return null}})()",
                    )
                    .put(
                        DATA_SYNC_ID,
                        "(function(){try{return window.yt&&window.yt.config_?window.yt.config_.DATASYNC_ID:null}catch(e){return null}})()",
                    )
                    .put(
                        AUTH_USER,
                        "(function(){try{return window.yt&&window.yt.config_?String(window.yt.config_.SESSION_INDEX||0):null}catch(e){return null}})()",
                    ),
            )
            .toString()
    }

    fun storePageContext(context: Context, payload: JSONObject, signedIn: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!signedIn) {
            prefs.edit().remove(VISITOR_DATA).remove(DATA_SYNC_ID).remove(AUTH_USER).apply()
            return
        }

        val visitorData = payload.optString(VISITOR_DATA).trim()
        val dataSyncId = payload.optString(DATA_SYNC_ID).trim().substringBefore("||")
        val authUser = payload.optString(AUTH_USER).filter(Char::isDigit).ifBlank { "0" }

        prefs.edit()
            .apply {
                if (visitorData.isNotBlank()) putString(VISITOR_DATA, visitorData) else remove(VISITOR_DATA)
                if (dataSyncId.isNotBlank()) putString(DATA_SYNC_ID, dataSyncId) else remove(DATA_SYNC_ID)
                putString(AUTH_USER, authUser)
            }
            .apply()

        if (visitorData.isNotBlank()) YouTube.visitorData = visitorData
        if (dataSyncId.isNotBlank()) YouTube.dataSyncId = dataSyncId
    }
}
