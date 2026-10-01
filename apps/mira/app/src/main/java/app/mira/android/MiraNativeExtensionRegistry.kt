package app.mira.android

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import app.mira.runtime.MiraSourceRegistry
import app.mira.source.MIRA_EXTENSION_API_VERSION
import app.mira.source.MiraSource
import app.mira.source.MiraExtensionHost
import app.mira.source.MiraExtensionManifest
import app.mira.source.MiraExtensionProvider
import app.mira.source.MiraExtensionIdentity
import dalvik.system.PathClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class MiraNativeExtensionRegistry(
    context: Context,
) : MiraSourceRegistry {

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        "mira_native_extension_preferences",
        Context.MODE_PRIVATE,
    )

    private val host = object : MiraExtensionHost {
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

    override suspend fun installedSources(): List<MiraSource> = withContext(Dispatchers.IO) {
        installedExtensionPackages()
            .flatMap { info ->
                runCatching { loadPackage(info) }
                    .onFailure { failure ->
                        Log.w(
                            LOG_TAG,
                            "Mira extension ${info.packageName} could not be loaded",
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
            MiraExtensionIdentity.acceptsPackage(info.packageName) && info.reqFeatures.orEmpty().any {
                it.name == MiraExtensionManifest.FEATURE
            }
        }
    }

    private fun loadPackage(info: PackageInfo): List<MiraSource> {
        val appInfo = info.applicationInfo
            ?: error("Extension package has no application info")
        val metadata = appInfo.metaData
            ?: error("Extension package has no Mira metadata")
        val declaredApi = metadata.getInt(MiraExtensionManifest.META_API_VERSION, 0)
        require(declaredApi == MIRA_EXTENSION_API_VERSION) {
            "Unsupported Mira extension API $declaredApi"
        }
        val providerName = metadata.getString(MiraExtensionManifest.META_PROVIDER_CLASS)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: error("Mira extension provider class is missing")
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
            .newInstance() as? MiraExtensionProvider
            ?: error("Declared provider does not implement MiraExtensionProvider")

        require(provider.apiVersion == MIRA_EXTENSION_API_VERSION) {
            "Provider targets Mira extension API ${provider.apiVersion}"
        }
        require(provider.extensionId == info.packageName) {
            "Mira extensionId must equal its Mira APK package name"
        }

        val displayName = metadata.getString(MiraExtensionManifest.META_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: provider.displayName.takeIf { it.isNotBlank() }
            ?: appContext.packageManager.getApplicationLabel(appInfo).toString()
        val versionName = info.versionName ?: "unknown"

        return provider.sources(host).map { source ->
            require(MiraExtensionIdentity.acceptsSource(provider.extensionId, source.metadata.id)) {
                "Mira source IDs must be scoped to the provider package"
            }
            val extensionInfo = InstalledExtensionInfo(
                extensionId = provider.extensionId,
                packageName = info.packageName,
                extensionName = displayName,
                versionName = versionName,
                apiVersion = declaredApi,
            )
            InstalledMiraSource(source, extensionInfo)
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

    private open class InstalledMiraSource(
        private val delegate: MiraSource,
        private val extensionInfo: InstalledExtensionInfo,
    ) : MiraSource by delegate {
        override val metadata = delegate.metadata.copy(
            extensionPackage = extensionInfo.packageName,
            extensionVersion = extensionInfo.versionName,
            extensionApiVersion = extensionInfo.apiVersion,
        )
    }

    private companion object {
        const val LOG_TAG = "MiraExtension"
    }
}
