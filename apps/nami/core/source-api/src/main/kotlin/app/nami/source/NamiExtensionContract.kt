package app.nami.source

/**
 * Stable v1 API for extensions written specifically for Nami.
 *
 * Extension APKs should compile against this API as compile-only/provided code so Nami owns the
 * runtime classes and domain model identity.
 */
const val NAMI_EXTENSION_API_VERSION: Int = 1

object NamiExtensionManifest {
    const val FEATURE = "app.nami.extension"
    const val META_PROVIDER_CLASS = "app.nami.extension.provider"
    const val META_API_VERSION = "app.nami.extension.api"
    const val META_NAME = "app.nami.extension.name"
}

interface NamiExtensionHost {
    fun getPreference(extensionId: String, sourceId: String, key: String): String?
    fun putPreference(extensionId: String, sourceId: String, key: String, value: String)
    fun removePreference(extensionId: String, sourceId: String, key: String)
}

interface NamiExtensionProvider {
    val extensionId: String
    val displayName: String
    val apiVersion: Int get() = NAMI_EXTENSION_API_VERSION

    fun sources(host: NamiExtensionHost): List<NamiAnimeSource>
}

interface NamiConfigurableSource {
    fun settings(): List<NamiSourceSetting>
}

sealed interface NamiSourceSetting {
    val key: String
    val title: String
    val summary: String?

    data class Toggle(
        override val key: String,
        override val title: String,
        override val summary: String? = null,
        val defaultValue: Boolean = false,
    ) : NamiSourceSetting

    data class Text(
        override val key: String,
        override val title: String,
        override val summary: String? = null,
        val defaultValue: String = "",
        val secret: Boolean = false,
    ) : NamiSourceSetting

    data class Choice(
        override val key: String,
        override val title: String,
        override val summary: String? = null,
        val choices: List<String>,
        val defaultValue: String? = choices.firstOrNull(),
    ) : NamiSourceSetting
}

enum class NamiSourceErrorKind {
    NETWORK,
    TIMEOUT,
    VERIFICATION_REQUIRED,
    NOT_FOUND,
    INCOMPATIBLE,
    TEMPORARY,
    UNKNOWN,
    STREAM_UNAVAILABLE,
}

class NamiSourceException(
    val kind: NamiSourceErrorKind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
