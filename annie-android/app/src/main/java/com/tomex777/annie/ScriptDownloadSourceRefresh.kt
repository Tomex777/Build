package com.tomex777.annie

import android.content.Context
import org.json.JSONObject

/** Optional package-owned URL refresh; the transport keeps its existing partial bytes. */
internal object ScriptDownloadSourceRefresh {
    suspend fun refresh(context: Context, item: DownloadItem): AnnieDownloadSource? {
        val scriptId = item.ownerScriptId ?: return null
        val action = item.refreshAction ?: return null
        val workspace = ScriptWorkspace(context)
        return try {
            val project = workspace.files.listProjects().firstOrNull { it.id == scriptId && it.enabled } ?: return null
            if (project.hasPackageManifest &&
                (NETWORK_ACCESS_CAPABILITY !in project.manifest.capabilities ||
                    NETWORK_ACCESS_PERMISSION !in workspace.files.grantedPermissions(scriptId))) return null
            workspace.reload()
            val result = workspace.executeAction(scriptId, action, item.refreshPayloadJson, "download:${item.id}", System.nanoTime()) ?: return null
            val json = JSONObject(result.resultJson)
            if (json.optString("type") == "error") return null
            ScriptVideoDownloadSource.from(json)
        } finally { workspace.close() }
    }
}
