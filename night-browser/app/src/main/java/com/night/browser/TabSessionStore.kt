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
        require(file.baseFile.length() <= TabPersistencePolicy.MAX_MANIFEST_BYTES)
        val data = file.readFully()
        require(data.size in 30..TabPersistencePolicy.MAX_MANIFEST_BYTES && data[0] == 1.toByte())
        val nonce = data.copyOfRange(1, 13)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, nonce))
        val root = JSONObject(String(cipher.doFinal(data.copyOfRange(13, data.size)), Charsets.UTF_8))
        require(root.optInt("version") == 1)
        val list = root.optJSONArray("tabs") ?: JSONArray()
        val tabs = buildList {
            for (i in 0 until minOf(list.length(), TabPersistencePolicy.MAX_TABS)) {
                val value = list.optJSONObject(i) ?: continue
                val url = TabPersistencePolicy.sanitizeUrl(value.optString("url")) ?: continue
                val id = value.optString("id")
                val journey = value.optString("journey")
                if (!TabPersistencePolicy.validId(id) || !TabPersistencePolicy.validId(journey)) continue
                add(Entry(
                    id = id,
                    journeyId = journey,
                    url = url,
                    title = value.optString("title").take(180),
                    lastNode = value.optString("node").takeIf(TabPersistencePolicy::validId),
                    sessionState = TabPersistencePolicy.safeState(value.optString("state"))
                ))
            }
        }
        val uniqueTabs = tabs.distinctBy { it.id }
        val active = root.optString("active").takeIf { id -> uniqueTabs.any { it.id == id } }
        Snapshot(active, uniqueTabs)
    }.getOrNull()

    /**
     * Caller provides only regular tabs. Never call this with private session information.
     * write() performs a disk fsync; invoke from an executor rather than the main thread.
     */
    fun write(snapshot: Snapshot) {
        val rows = JSONArray()
        val writtenIds = mutableSetOf<String>()
        snapshot.tabs.take(TabPersistencePolicy.MAX_TABS).forEach { tab ->
            val url = TabPersistencePolicy.sanitizeUrl(tab.url) ?: return@forEach
            if (!TabPersistencePolicy.validId(tab.id) ||
                !TabPersistencePolicy.validId(tab.journeyId) ||
                !writtenIds.add(tab.id)) return@forEach
            rows.put(JSONObject()
                .put("id", tab.id).put("journey", tab.journeyId)
                .put("url", url).put("title", tab.title.take(180))
                .put("node", tab.lastNode?.takeIf(TabPersistencePolicy::validId) ?: "")
                // Oversized Gecko SessionState is omitted in full, never truncated into
                // malformed JSON. URL-only restoration remains available.
                .put("state", TabPersistencePolicy.safeState(tab.sessionState) ?: ""))
        }
        val json = JSONObject().put("version", 1)
            .put("active", snapshot.activeId?.takeIf { it in writtenIds } ?: "").put("tabs", rows)
            .toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val nonce = cipher.iv
        require(nonce.size == 12)
        val payload = byteArrayOf(1) + nonce + cipher.doFinal(json)
        require(payload.size <= TabPersistencePolicy.MAX_MANIFEST_BYTES) {
            "Tab session manifest exceeds the bounded encrypted storage size"
        }
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

}
