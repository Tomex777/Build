package com.night.sora.extension

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
        val matches = pm.queryIntentServices(intent, PackageManager.GET_META_DATA)

        if (matches.isEmpty()) {
            callback(emptyList())
            return
        }

        val found = mutableListOf<InstalledExtension>()
        var remaining = matches.size

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

        matches.forEach { match ->
            val info = match.serviceInfo
            val metadata = info.metaData
            val id = metadata?.getString(ExtensionContract.META_EXTENSION_ID).orEmpty()
            val name = metadata?.getString(ExtensionContract.META_EXTENSION_NAME)
                ?: info.loadLabel(pm).toString()
            val apiVersion = metadata?.getInt(ExtensionContract.META_API_VERSION, -1) ?: -1
            val component = ComponentName(info.packageName, info.name)
            val base = InstalledExtension(info.packageName, component, id, name, apiVersion)

            if (apiVersion != ExtensionContract.API_VERSION) {
                finishOne(base.copy(error = "Unsupported API v$apiVersion"))
            } else {
                ExtensionClient(context, component).call(ExtensionContract.Method.MANIFEST) { result ->
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
