package com.tomex777.annie

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

internal enum class ScriptEnvFieldType(val wireName: String) {
    SWITCH("switch"),
    TEXT("text"),
    SECRET("secret"),
    NUMBER("number"),
    SELECT("select"),
    MULTI_SELECT("multi-select"),
    SLIDER("slider"),
    ACTION("button");

    companion object {
        fun fromWire(value: String): ScriptEnvFieldType? =
            entries.firstOrNull { it.wireName == value.lowercase() || (it == ACTION && value.equals("action", true)) }
    }
}

internal data class ScriptEnvField(
    val key: String,
    val type: ScriptEnvFieldType,
    val label: String,
    val description: String = "",
    val options: List<String> = emptyList(),
    val defaultValue: Any? = null,
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val scriptWritable: Boolean = false,
    val action: String? = null,
)

internal data class ScriptEnvDefinition(
    val title: String,
    val description: String = "",
    val icon: String? = null,
    val fields: List<ScriptEnvField>,
) {
    fun field(key: String): ScriptEnvField? = fields.firstOrNull { it.key == key }

    companion object {
        fun decode(raw: String): ScriptEnvDefinition {
            val json = JSONObject(raw)
            val fieldsJson = json.optJSONArray("fields") ?: JSONArray()
            val fields = buildList {
                val seen = linkedSetOf<String>()
                for (index in 0 until fieldsJson.length()) {
                    val item = fieldsJson.optJSONObject(index) ?: continue
                    val key = item.optString("key").trim()
                    require(key.matches(Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}"))) { "Invalid ENV key: $key" }
                    require(seen.add(key)) { "Duplicate ENV key: $key" }
                    val type = ScriptEnvFieldType.fromWire(item.optString("type"))
                        ?: error("Unsupported ENV field type for $key")
                    val options = item.optJSONArray("options").stringList()
                    if (type == ScriptEnvFieldType.SELECT || type == ScriptEnvFieldType.MULTI_SELECT) {
                        require(options.isNotEmpty()) { "ENV field $key requires options" }
                    }
                    val default = item.opt("default").takeUnless { it == null || it == JSONObject.NULL }
                    add(
                        ScriptEnvField(
                            key = key,
                            type = type,
                            label = item.optString("label").ifBlank { key },
                            description = item.optString("description"),
                            options = options,
                            defaultValue = default,
                            min = item.optDouble("min").takeIf { item.has("min") && it.isFinite() },
                            max = item.optDouble("max").takeIf { item.has("max") && it.isFinite() },
                            step = item.optDouble("step").takeIf { item.has("step") && it.isFinite() && it > 0.0 },
                            scriptWritable = item.optBoolean("scriptWritable", false),
                            action = item.optString("action").takeIf(String::isNotBlank),
                        )
                    )
                }
            }
            return ScriptEnvDefinition(
                title = json.optString("title").ifBlank { "Script configuration" },
                description = json.optString("description"),
                icon = json.optString("icon").takeIf(String::isNotBlank),
                fields = fields,
            )
        }
    }
}

internal class ScriptEnvStore(
    context: Context,
    scriptId: String,
) {
    private val appContext = context.applicationContext
    private val safeId = scriptId.replace(Regex("[^A-Za-z0-9_-]"), "_")
    private val values = appContext.getSharedPreferences("annie_script_env_$safeId", Context.MODE_PRIVATE)
    private val secrets = appContext.getSharedPreferences("annie_script_env_secrets_$safeId", Context.MODE_PRIVATE)

    fun value(field: ScriptEnvField): Any? {
        require(field.type != ScriptEnvFieldType.SECRET) { "Secret ENV values require annie.env.secret(key)" }
        if (field.type == ScriptEnvFieldType.ACTION) return null
        val raw = values.getString(field.key, null)
        return if (raw == null) normalize(field, field.defaultValue) else decodeValue(raw)
    }

    fun values(definition: ScriptEnvDefinition?): JSONObject {
        val output = JSONObject()
        definition?.fields.orEmpty().forEach { field ->
            if (field.type != ScriptEnvFieldType.SECRET && field.type != ScriptEnvFieldType.ACTION) {
                output.put(field.key, value(field) ?: JSONObject.NULL)
            }
        }
        return output
    }

    fun setFromUi(field: ScriptEnvField, newValue: Any?) {
        if (field.type == ScriptEnvFieldType.ACTION) return
        if (field.type == ScriptEnvFieldType.SECRET) {
            val text = newValue?.toString().orEmpty()
            if (text.isBlank()) secrets.edit().remove(field.key).apply()
            else secrets.edit().putString(field.key, encrypt(text)).apply()
            return
        }
        val normalized = normalize(field, newValue)
        values.edit().putString(field.key, encodeValue(normalized)).apply()
    }

    fun setFromScript(field: ScriptEnvField, newValue: Any?) {
        require(field.scriptWritable) { "ENV field ${field.key} is read-only for scripts" }
        require(field.type != ScriptEnvFieldType.SECRET) { "Scripts cannot mutate secret ENV fields" }
        require(field.type != ScriptEnvFieldType.ACTION) { "Action ENV fields do not store values" }
        setFromUi(field, newValue)
    }

    fun secret(field: ScriptEnvField): String? {
        require(field.type == ScriptEnvFieldType.SECRET) { "ENV field ${field.key} is not secret" }
        val encoded = secrets.getString(field.key, null) ?: return null
        return runCatching { decrypt(encoded) }.getOrNull()
    }

    fun hasSecret(field: ScriptEnvField): Boolean =
        field.type == ScriptEnvFieldType.SECRET && !secrets.getString(field.key, null).isNullOrBlank()

    private fun normalize(field: ScriptEnvField, value: Any?): Any? = when (field.type) {
        ScriptEnvFieldType.SWITCH -> when (value) {
            is Boolean -> value
            is String -> value.equals("true", true)
            is Number -> value.toInt() != 0
            else -> false
        }
        ScriptEnvFieldType.NUMBER, ScriptEnvFieldType.SLIDER -> {
            val number = when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            } ?: 0.0
            number.coerceIn(field.min ?: -Double.MAX_VALUE, field.max ?: Double.MAX_VALUE)
        }
        ScriptEnvFieldType.SELECT -> {
            val text = value?.toString().orEmpty()
            if (text in field.options) text else field.options.firstOrNull()
        }
        ScriptEnvFieldType.MULTI_SELECT -> {
            val requested = when (value) {
                is JSONArray -> value.stringList()
                is Collection<*> -> value.mapNotNull { it?.toString() }
                else -> emptyList()
            }
            JSONArray(requested.filter { it in field.options }.distinct())
        }
        ScriptEnvFieldType.TEXT -> value?.toString().orEmpty()
        ScriptEnvFieldType.SECRET, ScriptEnvFieldType.ACTION -> value
    }

    private fun encodeValue(value: Any?): String =
        JSONObject().put("value", value ?: JSONObject.NULL).toString()

    private fun decodeValue(raw: String): Any? =
        runCatching { JSONObject(raw).opt("value").takeUnless { it == JSONObject.NULL } }.getOrNull()

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val payload = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > IV_BYTES) { "Invalid encrypted ENV value" }
        val iv = payload.copyOfRange(0, IV_BYTES)
        val cipherText = payload.copyOfRange(IV_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(cipherText).toString(Charsets.UTF_8)
    }

    private fun masterKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "annie_script_env_master_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
    }
}

private fun JSONArray?.stringList(): List<String> {
    val array = this ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
        }
    }
}
