package com.night.sora.tumblrmemeextension

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.night.sora.extension.api.ExtensionContract
import com.night.sora.extension.api.ExtensionDescriptor
import com.night.sora.extension.api.ExtensionPermission
import com.night.sora.extension.api.SourceDescriptor
import com.night.sora.extension.api.toJson
import org.json.JSONObject
import java.util.concurrent.Executors

class TumblrMemeExtensionService : Service() {
    private val executor = Executors.newFixedThreadPool(2)

    private val apiKey: String by lazy {
        runCatching {
            packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                .metaData
                ?.getString("sora.tumblr.apiKey")
                .orEmpty()
        }.getOrDefault("")
    }

    private val descriptor = ExtensionDescriptor(
        id = "night.sora.memes.tumblr",
        name = "Tumblr Memes",
        version = "0.1.0",
        apiVersion = ExtensionContract.API_VERSION,
        author = "Night",
        description = "External Tumblr tagged meme source for Sora.",
        contentTypes = setOf("memes"),
        capabilities = setOf("catalog", "browse", "search", "details", "feed"),
        permissions = listOf(
            ExtensionPermission("network", listOf("api.tumblr.com", "www.tumblr.com", "64.media.tumblr.com")),
        ),
        sources = listOf(
            SourceDescriptor(
                id = "tumblr.memes",
                name = "Tumblr · #memes",
                contentTypes = setOf("memes"),
                capabilities = setOf("browse", "search", "details", "feed"),
            ),
        ),
    )

    private val messenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != ExtensionContract.MSG_REQUEST) return
            val callerPackages = packageManager.getPackagesForUid(msg.sendingUid).orEmpty().toSet()
            if ("com.night.sora" !in callerPackages) {
                respond(msg, Result.failure(IllegalAccessException("Caller is not Sora Core")))
                return
            }

            val requestId = msg.data.getString(ExtensionContract.KEY_REQUEST_ID).orEmpty()
            val method = msg.data.getString(ExtensionContract.KEY_METHOD).orEmpty()
            val payload = runCatching {
                JSONObject(msg.data.getString(ExtensionContract.KEY_PAYLOAD_JSON).orEmpty().ifBlank { "{}" })
            }.getOrDefault(JSONObject())
            val replyTo = msg.replyTo

            executor.execute {
                val result = runCatching {
                    when (method) {
                        ExtensionContract.Method.MANIFEST -> descriptor.toJson()
                        ExtensionContract.Method.BROWSE -> TumblrMemeCatalog.browse(apiKey)
                        ExtensionContract.Method.SEARCH -> TumblrMemeCatalog.search(payload.optString("query"), apiKey)
                        ExtensionContract.Method.DETAILS -> TumblrMemeCatalog.details(payload.optString("id"), apiKey)
                        ExtensionContract.Method.FEED -> TumblrMemeCatalog.browse(apiKey)
                        else -> error("Unsupported method: $method")
                    }
                }
                val response = Message.obtain(null, ExtensionContract.MSG_RESPONSE).apply {
                    data = Bundle().apply {
                        putString(ExtensionContract.KEY_REQUEST_ID, requestId)
                        putBoolean(ExtensionContract.KEY_OK, result.isSuccess)
                        result.onSuccess { putString(ExtensionContract.KEY_RESULT_JSON, it) }
                        result.onFailure { putString(ExtensionContract.KEY_ERROR, it.message ?: "Tumblr meme source error") }
                    }
                }
                runCatching { replyTo?.send(response) }
            }
        }
    })

    private fun respond(msg: Message, result: Result<String>) {
        val response = Message.obtain(null, ExtensionContract.MSG_RESPONSE).apply {
            data = Bundle().apply {
                putString(ExtensionContract.KEY_REQUEST_ID, msg.data.getString(ExtensionContract.KEY_REQUEST_ID).orEmpty())
                putBoolean(ExtensionContract.KEY_OK, result.isSuccess)
                result.onSuccess { putString(ExtensionContract.KEY_RESULT_JSON, it) }
                result.onFailure { putString(ExtensionContract.KEY_ERROR, it.message ?: "Extension error") }
            }
        }
        runCatching { msg.replyTo?.send(response) }
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
