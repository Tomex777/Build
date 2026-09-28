package app.nami.android

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import app.nami.runtime.NamiSourceRegistry
import app.nami.source.NAMI_EXTENSION_API_VERSION
import app.nami.source.NamiAnimeSource
import app.nami.source.NamiConfigurableSource
import app.nami.source.NamiExtensionHost
import app.nami.source.NamiExtensionManifest
import app.nami.source.NamiExtensionProvider
import app.nami.source.SourceOrigin
import dalvik.system.PathClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class NamiNativeExtensionRegistry(
    context: Context,
) : NamiSourceRegistry {

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        "nami_native_extension_preferences",
        Context.MODE_PRIVATE,
    )

    private val host = object : NamiExtensionHost {
        override fun getPreference(extensionId: String, sourceId: String, key: String): String? =
            preferences.getString(preferenceKey(extensionId, sourceId, key), null)

        override fun putPreference(
            extensionId: String,
            sourceId: String,
            key: String,
            value: String,
        ) {
            preferences.edit()
                .putString(preferenceKey(extensionId, sourceId, key), value)
                .apply()
        }

        override fun removePreference(extensionId: String, sourceId: String, key: String) {
            preferences.edit()
                .remove(preferenceKey(extensionId, sourceId, key))
                .apply()
        }
    }

    override suspend fun installedSources(): List<NamiAnimeSource> = withContext(Dispatchers.IO) {
        installedExtensionPackages()
            .flatMap { info ->
                runCatching { loadPackage(info) }
                    .onFailure { failure ->
                        Log.w(
                            LOG_TAG,
                            "Nami extension ${info.packageName} could not be loaded",
                            failure,
                        )
                    }
                    .getOrDefault(emptyList())
            }
            .distinctBy { it.metadata.id }
            .sortedBy { it.metadata.name.lowercase() }
    }

    private fun installedExtensionPackages(): List<PackageInfo> {
        val flags = PackageManager.GET_CONFIGURATIONS or PackageManager.GET_META_DATA
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.packageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(flags.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.getInstalledPackages(flags)
        }
        return packages.filter { info ->
            info.reqFeatures.orEmpty().any {
                it.name == NamiExtensionManifest.FEATURE
            }
        }
    }

    private fun loadPackage(info: PackageInfo): List<NamiAnimeSource> {
        val appInfo = info.applicationInfo
            ?: error("Extension package has no application info")
        val metadata = appInfo.metaData
            ?: error("Extension package has no Nami metadata")
        val declaredApi = metadata.getInt(NamiExtensionManifest.META_API_VERSION, 0)
        require(declaredApi == NAMI_EXTENSION_API_VERSION) {
            "Unsupported Nami extension API $declaredApi"
        }
        val providerName = metadata.getString(NamiExtensionManifest.META_PROVIDER_CLASS)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: error("Nami extension provider class is missing")
        val className = if (providerName.startsWith('.')) {
            info.packageName + providerName
        } else {
            providerName
        }
        val loader = PathClassLoader(
            appInfo.sourceDir,
            null,
            appContext.classLoader,
        )
        val provider = Class.forName(className, true, loader)
            .getDeclaredConstructor()
            .newInstance() as? NamiExtensionProvider
            ?: error("Declared provider does not implement NamiExtensionProvider")

        require(provider.apiVersion == NAMI_EXTENSION_API_VERSION) {
            "Provider targets Nami extension API ${provider.apiVersion}"
        }
        require(provider.extensionId.isNotBlank()) {
            "Nami extensionId must not be blank"
        }

        val displayName = metadata.getString(NamiExtensionManifest.META_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: provider.displayName.takeIf { it.isNotBlank() }
            ?: appContext.packageManager.getApplicationLabel(appInfo).toString()
        val versionName = info.versionName ?: "unknown"

        return provider.sources(host).map { source ->
            require(source.metadata.id.isNotBlank()) { "Nami source ID must not be blank" }
            require(source.metadata.origin == SourceOrigin.NATIVE_NAMI) {
                "Nami extension source ${source.metadata.id} must use NATIVE_NAMI origin"
            }
            val extensionInfo = InstalledExtensionInfo(
                extensionId = provider.extensionId,
                packageName = info.packageName,
                extensionName = displayName,
                versionName = versionName,
                apiVersion = declaredApi,
            )
            val configurable = source as? NamiConfigurableSource
            require(source.metadata.capabilities.configurable == (configurable != null)) {
                "Nami source ${source.metadata.id} must implement NamiConfigurableSource " +
                    "when its configurable capability is enabled"
            }
            if (configurable == null) {
                InstalledNamiSource(source, extensionInfo)
            } else {
                InstalledConfigurableNamiSource(source, configurable, extensionInfo, host)
            }
        }
    }

    private fun preferenceKey(extensionId: String, sourceId: String, key: String): String =
        extensionId + "\u0000" + sourceId + "\u0000" + key

    private data class InstalledExtensionInfo(
        val extensionId: String,
        val packageName: String,
        val extensionName: String,
        val versionName: String,
        val apiVersion: Int,
    )

    private open class InstalledNamiSource(
        private val delegate: NamiAnimeSource,
        private val extensionInfo: InstalledExtensionInfo,
    ) : NamiAnimeSource by delegate {
        override val metadata = delegate.metadata.copy(
            extensionName = extensionInfo.extensionName,
            extensionPackage = extensionInfo.packageName,
            extensionVersion = extensionInfo.versionName,
            extensionApiVersion = extensionInfo.apiVersion,
            origin = SourceOrigin.NATIVE_NAMI,
        )
    }

    private class InstalledConfigurableNamiSource(
        delegate: NamiAnimeSource,
        configurable: NamiConfigurableSource,
        extensionInfo: InstalledExtensionInfo,
        host: NamiExtensionHost,
    ) : InstalledNamiSource(delegate, extensionInfo),
        NamiConfigurableSource by configurable,
        NamiNativeConfigurationHandle by HostBackedNamiConfiguration(
            host = host,
            extensionId = extensionInfo.extensionId,
            sourceId = delegate.metadata.id,
        )

    private class HostBackedNamiConfiguration(
        private val host: NamiExtensionHost,
        private val extensionId: String,
        private val sourceId: String,
    ) : NamiNativeConfigurationHandle {
        override fun getPreference(key: String): String? =
            host.getPreference(extensionId, sourceId, key)

        override fun putPreference(key: String, value: String) =
            host.putPreference(extensionId, sourceId, key, value)

        override fun removePreference(key: String) =
            host.removePreference(extensionId, sourceId, key)
    }

    private companion object {
        const val LOG_TAG = "NamiExtension"
    }
}

/** Host-owned storage bridge used by Nami's native configuration renderer. */
internal interface NamiNativeConfigurationHandle {
    fun getPreference(key: String): String?
    fun putPreference(key: String, value: String)
    fun removePreference(key: String)
}
