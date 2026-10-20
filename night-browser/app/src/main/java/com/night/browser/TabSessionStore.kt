package com.night.browser

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Device-local encrypted normal-tab session manifest.
 *
 * Gecko SessionState contains history and possibly form data. Never place it in plaintext
 * SharedPreferences or a JSON cache. Android's keystore holds the non-exportable AES key.
 * Incognito tabs are deliberately excluded by the controller and are never read from disk.
 */
internal class TabSessionStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "normal-tabs-v1.bin"))
    private val alias = "night.browser.tabs.v1"

    data class Entry(
        val id: String,
        val journeyId: String,
        val url: String,
        val title: String,
        val lastNode: String?,
        val sessionState: String?
    )
    data class Snapshot(val activeId: String?, val tabs: List<Entry>)

    fun read(): Snapshot? = runCatching {
        val data = file.readFully()
        require(data.size > 29 && data[0] == 1.toByte())
        val nonce = data.copyOfRange(1, 13)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, nonce))
        val root = JSONObject(String(cipher.doFinal(data.copyOfRange(13, data.size)), Charsets.UTF_8))
        require(root.optInt("version") == 1)
        val list = root.optJSONArray("tabs") ?: JSONArray()
        val tabs = buildList {
            for (i in 0 until minOf(list.length(), 80)) {
                val value = list.optJSONObject(i) ?: continue
                val url = value.optString("url").take(4096)
                if (url.isNotBlank() && !isWebUrl(url)) continue
                val id = value.optString("id")
                val journey = value.optString("journey")
                if (id.isBlank() || journey.isBlank()) continue
                add(Entry(
                    id = id,
                    journeyId = journey,
                    url = url,
                    title = value.optString("title").take(180),
                    lastNode = value.optString("node").ifBlank { null },
                    sessionState = value.optString("state").takeIf { it.isNotBlank() }
                ))
            }
        }
        Snapshot(root.optString("active").ifBlank { null }, tabs.distinctBy { it.id })
    }.getOrNull()

    /**
     * Caller provides only regular tabs. Never call this with private session information.
     * write() performs a disk fsync; invoke from an executor rather than the main thread.
     */
    fun write(snapshot: Snapshot) {
        val rows = JSONArray()
        snapshot.tabs.take(80).forEach { tab ->
            if (tab.url.isNotBlank() && !isWebUrl(tab.url)) return@forEach
            rows.put(JSONObject()
                .put("id", tab.id).put("journey", tab.journeyId)
                .put("url", tab.url.take(4096)).put("title", tab.title.take(180))
                .put("node", tab.lastNode ?: "")
                // Large session states can include oversized pages. URL restoration is fallback.
                .put("state", tab.sessionState?.takeIf { it.length <= 128_000 } ?: ""))
        }
        val json = JSONObject().put("version", 1)
            .put("active", snapshot.activeId ?: "").put("tabs", rows)
            .toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val nonce = cipher.iv
        require(nonce.size == 12)
        val payload = byteArrayOf(1) + nonce + cipher.doFinal(json)
        val stream = file.startWrite()
        try {
            stream.write(payload)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    private fun getKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build()
        )
        return generator.generateKey()
    }

    private fun isWebUrl(value: String) =
        value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true)
}
