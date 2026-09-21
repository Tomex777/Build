package com.example.whatsapp.extensions.tools

import android.content.Context
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class NightMcpServerConfig(
    val id: String,
    val displayName: String,
    val endpoint: String,
    val secretAlias: String,
    val enabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)

data class NightMcpServerSummary(
    val config: NightMcpServerConfig,
    val hasBearerToken: Boolean,
)

class NightMcpServerStore private constructor(
    context: Context,
) {
    private val app = context.applicationContext
    private val prefs =
        app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secrets = NightSecretStoreFacade(app)

    fun list(): List<NightMcpServerSummary> =
        readConfigs()
            .sortedWith(
                compareByDescending<NightMcpServerConfig> { it.enabled }
                    .thenBy { it.displayName.lowercase() }
            )
            .map { config ->
                NightMcpServerSummary(
                    config = config,
                    hasBearerToken =
                        secrets.contains(config.secretAlias),
                )
            }

    fun get(id: String): NightMcpServerSummary? =
        list().firstOrNull { it.config.id == id }

    fun bearerToken(config: NightMcpServerConfig): String? =
        secrets.get(config.secretAlias)

    fun upsert(
        existingId: String? = null,
        displayName: String,
        endpoint: String,
        bearerToken: String? = null,
        clearBearerToken: Boolean = false,
        enabled: Boolean = true,
    ): NightMcpServerConfig {
        val normalizedName = displayName.trim()
        val normalizedEndpoint = endpoint.trim().trimEnd('/')
        require(normalizedName.isNotBlank()) {
            "Server name is required."
        }
        require(
            normalizedEndpoint.startsWith("https://") ||
                normalizedEndpoint.startsWith("http://")
        ) {
            "MCP endpoint must use HTTP or HTTPS."
        }

        val now = System.currentTimeMillis()
        val existing = existingId
            ?.let { id -> readConfigs().firstOrNull { it.id == id } }
        val id = existing?.id ?: UUID.randomUUID().toString()
        val secretAlias =
            existing?.secretAlias ?: "mcp_server_" + id

        when {
            clearBearerToken ->
                secrets.remove(secretAlias)
            !bearerToken.isNullOrBlank() ->
                secrets.put(secretAlias, bearerToken.trim())
        }

        val updated = NightMcpServerConfig(
            id = id,
            displayName = normalizedName,
            endpoint = normalizedEndpoint,
            secretAlias = secretAlias,
            enabled = enabled,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )

        val configs =
            readConfigs().filterNot { it.id == id } + updated
        writeConfigs(configs)
        return updated
    }

    fun setEnabled(
        id: String,
        enabled: Boolean,
    ): NightMcpServerConfig {
        val configs = readConfigs()
        val current = configs.firstOrNull { it.id == id }
            ?: error("MCP server was not found.")
        val updated = current.copy(
            enabled = enabled,
            updatedAt = System.currentTimeMillis(),
        )
        writeConfigs(
            configs.map {
                if (it.id == id) updated else it
            }
        )
        return updated
    }

    fun delete(id: String) {
        val configs = readConfigs()
        val current = configs.firstOrNull { it.id == id }
            ?: return
        secrets.remove(current.secretAlias)
        writeConfigs(configs.filterNot { it.id == id })
    }

    private fun readConfigs(): List<NightMcpServerConfig> {
        val raw = prefs.getString(KEY_SERVERS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").trim()
                    val name = item.optString("displayName").trim()
                    val endpoint = item.optString("endpoint").trim()
                    val secretAlias = item.optString("secretAlias").trim()
                    if (
                        id.isBlank() ||
                        name.isBlank() ||
                        endpoint.isBlank() ||
                        secretAlias.isBlank()
                    ) {
                        continue
                    }
                    add(
                        NightMcpServerConfig(
                            id = id,
                            displayName = name,
                            endpoint = endpoint,
                            secretAlias = secretAlias,
                            enabled = item.optBoolean("enabled", true),
                            createdAt = item.optLong("createdAt", 0L),
                            updatedAt = item.optLong("updatedAt", 0L),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeConfigs(configs: List<NightMcpServerConfig>) {
        val array = JSONArray()
        configs.forEach { config ->
            array.put(
                JSONObject()
                    .put("id", config.id)
                    .put("displayName", config.displayName)
                    .put("endpoint", config.endpoint)
                    .put("secretAlias", config.secretAlias)
                    .put("enabled", config.enabled)
                    .put("createdAt", config.createdAt)
                    .put("updatedAt", config.updatedAt)
            )
        }
        prefs.edit().putString(KEY_SERVERS, array.toString()).apply()
    }

    private class NightSecretStoreFacade(
        context: Context,
    ) {
        private val store =
            com.example.whatsapp.data.night.NightSecretStore.get(context)

        fun put(alias: String, value: String) =
            store.put(alias, value)

        fun get(alias: String): String? =
            store.get(alias)

        fun remove(alias: String) =
            store.remove(alias)

        fun contains(alias: String): Boolean =
            store.contains(alias)
    }

    companion object {
        private const val PREFS_NAME = "night_mcp_servers"
        private const val KEY_SERVERS = "servers"

        @Volatile
        private var instance: NightMcpServerStore? = null

        fun get(context: Context): NightMcpServerStore =
            instance ?: synchronized(this) {
                instance
                    ?: NightMcpServerStore(context)
                        .also { instance = it }
            }
    }
}
