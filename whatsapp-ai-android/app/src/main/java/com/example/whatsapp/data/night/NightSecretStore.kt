package com.example.whatsapp.data.night

import android.content.Context
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class NightSecretStore private constructor(
    private val context: Context,
) {
    private val prefs = context.getSharedPreferences("night_secrets", Context.MODE_PRIVATE)

    fun put(alias: String, secret: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
        val encrypted = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        val packed = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        prefs.edit().putString(alias, packed).apply()
    }

    fun get(alias: String): String? {
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

    fun remove(alias: String) {
        prefs.edit().remove(alias).apply()
    }

    fun contains(alias: String): Boolean = prefs.contains(alias)

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

        @Volatile private var instance: NightSecretStore? = null

        fun get(context: Context): NightSecretStore =
            instance ?: synchronized(this) {
                instance ?: NightSecretStore(context.applicationContext)
                    .also { instance = it }
            }
    }
}
