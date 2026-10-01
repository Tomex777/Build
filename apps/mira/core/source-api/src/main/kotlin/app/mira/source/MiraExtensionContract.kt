package app.mira.source

/**
 * Stable v1 API for extensions written specifically for Mira.
 *
 * Extension APKs should compile against this API as compile-only/provided code so Mira owns the
 * runtime classes and domain model identity.
 */
const val MIRA_EXTENSION_API_VERSION: Int = 1

object MiraExtensionManifest {
    const val FEATURE = "app.mira.extension"
    const val META_PROVIDER_CLASS = "app.mira.extension.provider"
    const val META_API_VERSION = "app.mira.extension.api"
    const val META_NAME = "app.mira.extension.name"
}

interface MiraExtensionHost {
    fun getPreference(extensionId: String, sourceId: String, key: String): String?
    fun putPreference(extensionId: String, sourceId: String, key: String, value: String)
    fun removePreference(extensionId: String, sourceId: String, key: String)
}

interface MiraExtensionProvider {
    val extensionId: String
    val displayName: String
    val apiVersion: Int get() = MIRA_EXTENSION_API_VERSION

    fun sources(host: MiraExtensionHost): List<MiraSource>
}

interface MiraConfigurableSource {
    fun settings(): List<MiraSourceSetting>
}

sealed interface MiraSourceSetting {
    val key: String
    val title: String
    val summary: String?

    data class Toggle(
        override val key: String,
        override val title: String,
        override val summary: String? = null,
        val defaultValue: Boolean = false,
    ) : MiraSourceSetting

    data class Text(
        override val key: String,
        override val title: String,
        override val summary: String? = null,
        val defaultValue: String = "",
        val secret: Boolean = false,
    ) : MiraSourceSetting

    data class Choice(
        override val key: String,
        override val title: String,
        override val summary: String? = null,
        val choices: List<String>,
        val defaultValue: String? = choices.firstOrNull(),
    ) : MiraSourceSetting
}

enum class MiraSourceErrorKind {
    NETWORK,
    TIMEOUT,
    VERIFICATION_REQUIRED,
    NOT_FOUND,
    INCOMPATIBLE,
    TEMPORARY,
    UNKNOWN,
    STREAM_UNAVAILABLE,
}

class MiraSourceException(
    val kind: MiraSourceErrorKind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Reject foreign package/provider identities before loading extension bytecode. */
object MiraExtensionIdentity {
    const val PACKAGE_PREFIX = "app.mira.extension."
    fun acceptsPackage(packageName: String): Boolean =
        packageName.startsWith(PACKAGE_PREFIX) && packageName.length > PACKAGE_PREFIX.length
    fun acceptsSource(extensionId: String, sourceId: String): Boolean =
        acceptsPackage(extensionId) && sourceId.startsWith("$extensionId:") &&
            sourceId.length > extensionId.length + 1
}
