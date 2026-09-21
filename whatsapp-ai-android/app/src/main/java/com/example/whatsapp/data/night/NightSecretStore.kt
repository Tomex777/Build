package com.example.whatsapp.data.night

import android.content.Context
import android.util.Base64
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

data class NightProviderCredential(
    val id: String,
    val label: String,
    val secret: String,
)

data class NightProviderKeySummary(
    val id: String,
    val label: String,
    val suffix: String,
)

class NightSecretStore private constructor(
    private val context: Context,
) {
    private val prefs = context.getSharedPreferences("night_secrets", Context.MODE_PRIVATE)

    fun put(alias: String, secret: String) {
        writeEncrypted(alias, secret)
    }

    fun get(alias: String): String? =
        getProviderCredentials(alias).firstOrNull()?.secret

    fun getProviderCredentials(alias: String): List<NightProviderCredential> {
        val decoded = readEncrypted(alias) ?: return emptyList()
        if (!decoded.startsWith(KEY_POOL_PREFIX)) {
            return listOf(
                NightProviderCredential(
                    id = LEGACY_PRIMARY_ID,
                    label = "Key 1",
                    secret = decoded,
                )
            )
        }

        val raw = decoded.removePrefix(KEY_POOL_PREFIX)
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val secret = item.optString("secret").trim()
                    if (secret.isBlank()) continue
                    add(
                        NightProviderCredential(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            label = item.optString("label").trim().ifBlank { "Key " + (index + 1) },
                            secret = secret,
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun getProviderKeySummaries(alias: String): List<NightProviderKeySummary> =
        getProviderCredentials(alias).map { credential ->
            NightProviderKeySummary(
                id = credential.id,
                label = credential.label,
                suffix = credential.secret.takeLast(4).padStart(
                    credential.secret.takeLast(4).length,
                    '•',
                ),
            )
        }

    fun initializeProviderKeyPool(
        alias: String,
        secret: String,
        label: String = "Key 1",
    ) {
        require(secret.isNotBlank()) { "API key is required." }
        writeProviderCredentials(
            alias = alias,
            credentials = listOf(
                NightProviderCredential(
                    id = UUID.randomUUID().toString(),
                    label = label.trim().ifBlank { "Key 1" },
                    secret = secret.trim(),
                )
            ),
        )
    }

    fun addProviderCredential(
        alias: String,
        secret: String,
        label: String? = null,
    ): NightProviderCredential {
        val value = secret.trim()
        require(value.isNotBlank()) { "API key is required." }

        val existing = getProviderCredentials(alias)
        require(existing.none { it.secret == value }) { "That API key is already in this pool." }

        val credential = NightProviderCredential(
            id = UUID.randomUUID().toString(),
            label = label?.trim()?.ifBlank { null } ?: "Key " + (existing.size + 1),
            secret = value,
        )
        writeProviderCredentials(alias, existing + credential)
        return credential
    }

    fun removeProviderCredential(
        alias: String,
        credentialId: String,
    ): Boolean {
        val existing = getProviderCredentials(alias)
        if (existing.size <= 1) return false
        val updated = existing.filterNot { it.id == credentialId }
        if (updated.size == existing.size || updated.isEmpty()) return false
        writeProviderCredentials(alias, updated)
        return true
    }

    fun remove(alias: String) {
        prefs.edit().remove(alias).apply()
    }

    fun contains(alias: String): Boolean = prefs.contains(alias)

    private fun writeProviderCredentials(
        alias: String,
        credentials: List<NightProviderCredential>,
    ) {
        require(credentials.isNotEmpty()) { "A provider must keep at least one API key." }
        val array = JSONArray()
        credentials.forEach { credential ->
            array.put(
                JSONObject()
                    .put("id", credential.id)
                    .put("label", credential.label)
                    .put("secret", credential.secret)
            )
        }
        writeEncrypted(alias, KEY_POOL_PREFIX + array.toString())
    }

    private fun writeEncrypted(
        alias: String,
        plaintext: String,
    ) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val packed = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        prefs.edit().putString(alias, packed).apply()
    }

    private fun readEncrypted(alias: String): String? {
        val packed = prefs.getString(alias, null) ?: return null
        val parts = packed.split(":", limit = 2)
        if (parts.size != 2) return null

        return runCatching {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateMasterKey(),
                GCMParameterSpec(128, iv),
            )
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(MASTER_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance("AES", KEYSTORE)
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            MASTER_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val MASTER_ALIAS = "night_master_secret_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_POOL_PREFIX = "night:key-pool:v1:"
        private const val LEGACY_PRIMARY_ID = "legacy-primary"

        @Volatile private var instance: NightSecretStore? = null

        fun get(context: Context): NightSecretStore =
            instance ?: synchronized(this) {
                instance ?: NightSecretStore(context.applicationContext)
                    .also { instance = it }
            }
    }
}
