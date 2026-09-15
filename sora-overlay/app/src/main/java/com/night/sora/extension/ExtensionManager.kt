package com.night.sora.extension

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.night.sora.catalog.JikanCatalogService
import com.night.sora.extension.api.ExtensionContract
import com.night.sora.extension.api.ExtensionDescriptor
import com.night.sora.extension.api.extensionDescriptorFromJson

data class InstalledExtension(
    val packageName: String,
    val component: ComponentName,
    val declaredId: String,
    val declaredName: String,
    val apiVersion: Int,
    val descriptor: ExtensionDescriptor? = null,
    val error: String? = null,
)

class ExtensionManager(private val context: Context) {
    fun discover(callback: (List<InstalledExtension>) -> Unit) {
        val pm = context.packageManager
        val intent = Intent(ExtensionContract.ACTION_BIND_EXTENSION)
        val builtInJikan = InstalledExtension(
            packageName = context.packageName,
            component = ComponentName(context, JikanCatalogService::class.java),
            declaredId = "sora.core.jikan",
            declaredName = "Sora Anime & Manga Catalog",
            apiVersion = ExtensionContract.API_VERSION,
        )

        val external = pm.queryIntentServices(intent, PackageManager.GET_META_DATA)
            .mapNotNull { match ->
                val info = match.serviceInfo
                val component = ComponentName(info.packageName, info.name)
                if (component == builtInJikan.component) return@mapNotNull null

                val metadata = info.metaData
                InstalledExtension(
                    packageName = info.packageName,
                    component = component,
                    declaredId = metadata?.getString(ExtensionContract.META_EXTENSION_ID).orEmpty(),
                    declaredName = metadata?.getString(ExtensionContract.META_EXTENSION_NAME)
                        ?: info.loadLabel(pm).toString(),
                    apiVersion = metadata?.getInt(ExtensionContract.META_API_VERSION, -1) ?: -1,
                )
            }

        // Built-in catalog infrastructure is explicit Core state. PackageManager
        // discovery is reserved for separately-installed source APKs.
        val candidates = listOf(builtInJikan) + external
        val found = mutableListOf<InstalledExtension>()
        var remaining = candidates.size

        fun finishOne(extension: InstalledExtension) {
            found += extension
            remaining--
            if (remaining == 0) {
                callback(
                    found.sortedWith(
                        compareBy<InstalledExtension> { it.packageName.contains(".demo") }
                            .thenBy { it.declaredName.lowercase() }
                    )
                )
            }
        }

        candidates.forEach { base ->
            if (base.apiVersion != ExtensionContract.API_VERSION) {
                finishOne(base.copy(error = "Unsupported API v${base.apiVersion}"))
            } else {
                ExtensionClient(context, base.component).call(ExtensionContract.Method.MANIFEST) { result ->
                    result.fold(
                        onSuccess = { raw ->
                            runCatching { extensionDescriptorFromJson(raw) }
                                .onSuccess { finishOne(base.copy(descriptor = it)) }
                                .onFailure { finishOne(base.copy(error = "Invalid manifest: ${it.message}")) }
                        },
                        onFailure = { finishOne(base.copy(error = it.message ?: "Connection failed")) },
                    )
                }
            }
        }
    }

    fun call(extension: InstalledExtension, method: String, payloadJson: String, callback: (Result<String>) -> Unit) {
        ExtensionClient(context, extension.component).call(method, payloadJson, callback)
    }
}
