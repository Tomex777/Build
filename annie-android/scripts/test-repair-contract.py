#!/usr/bin/env python3
"""Fast, dependency-free fixture checks for the fail-closed merge repair transformations.

Fixtures intentionally contain signatures from the Oct 7 GitHub branch; this
exercises replacement/idempotence, not Android compilation or a live checkout.
"""
from pathlib import Path
from importlib.util import module_from_spec, spec_from_file_location
import sys
ROOT=Path(__file__).resolve().parents[1]
spec=spec_from_file_location('repair', ROOT/'scripts/repair-m0b-merge.py')
repair=module_from_spec(spec);spec.loader.exec_module(repair)

PKG='''internal data class AnniePackageManifest(
    val entryPoint: String,
    val requires: Map<String, String> = emptyMap(),
    val publisher: AnniePackagePublisher? = null,
    val networkHosts: Set<String> = emptySet(),
) {
    companion object { }
}
internal data class AnniePackagePublisher(
    val id: String,
    val name: String,
)
'''
ARCH='''private fun decodeManifest(
 json: JSONObject,
) {
        val apiVersion = json.optString("apiVersion").ifBlank { AnniePackageManifest.CURRENT_API_VERSION }
        require(apiVersion == AnniePackageManifest.CURRENT_API_VERSION) { "unsupported" }
        val requires = manifestObject(json,"requires")
        val publisher = manifestObject(json,"publisher")
        val networkHosts = manifestObject(json,"network")
        val entryPoint = json.optString("entryPoint")
    return AnniePackageManifest(
            entryPoint = entryPoint,
            requires = requires,
            publisher = publisher,
            networkHosts = networkHosts,
            permissions = permissions,
    )
}
private fun encodeManifest(manifest: AnniePackageManifest) = JSONObject()
        .put("requires", JSONObject().apply { manifest.requires.forEach { (key, value) -> put(key, value) } })
        .put("publisher", manifest.publisher?.let { JSONObject().put("id", it.id).put("name", it.name) } ?: JSONObject.NULL)
        .put("network", JSONObject().put("hosts", JSONArray(manifest.networkHosts.toList())))
        .put("permissions", JSONArray(manifest.permissions.toList()))
'''
DM='''data class DownloadItem(
    val refreshPayloadJson: String = "{}",
    val completionActionJson: String = "",
    val completionChatId: String? = null,
)
fun load() {
                        completionActionJson = json.optString("completionActionJson"),
                        completionChatId = json.optString("completionChatId").takeIf { it.isNotBlank() },
}
fun write() {
                    .put("completionActionJson", item.completionActionJson)
                    .put("completionChatId", item.completionChatId ?: "")
}
'''
DOWN='''fun finishFile(item: DownloadItem, temp: File, finalFile: File, mimeType: String?) {
        publish(
            item.copy(
                state = DownloadState.COMPLETE,
                progress = 1f,
                bytesDone = finalFile.length(),
                bytesTotal = finalFile.length(),
                localPath = finalFile.absolutePath,
                failureReason = "",
                sourceMimeType = mimeType ?: item.sourceMimeType,
            )
        )
    }

    private fun finalFile(item: DownloadItem) {}
'''
EXT='''fun ExtensionsManager(
    grantedPermissions: (ScriptProject) -> Set<String> = { emptySet() },
) {
    ExtensionProjectCard(project, grantedPermissions(project), onToggle, onConfigure, onOpenStudio) { pendingUninstall = project }
}
private fun ExtensionProjectCard(
    project: ScriptProject,
    grantedPermissions: Set<String>,
) {
                if (project.manifest.permissions.isNotEmpty()) {
}
fun extensionPermissionLabel(permission: String) = when (permission) {
    else -> if (permission.startsWith("service:")) {
}
'''
STUDIO='''private fun selected() {
                if (!isTextSelected()) return false
                deleteText()
                notifyIMEExternalCursorChange()
}
'''
BROWSER='''internal object AnnieBrowserSessionStore {
    fun scrollY(context: Context, spec: AnnieBrowserSpec): Int =
        prefs(context).getInt(key(spec.sanitized().sessionId, "scrollY"), 0).coerceAtLeast(0)
    fun clear() {
        p.edit().remove(key(sessionId, "url")).remove(key(sessionId, "spec"))
            .remove(key(sessionId, "state")).remove(key(sessionId, "verifiedAt")).remove(key(sessionId, "scrollY")).apply()
    }
}
'''
for name, source, fix in [('AnniePackage.kt',PKG,repair.package), ('AnniePackageArchive.kt',ARCH,repair.archive),('DownloadManager.kt',DM,repair.download_manager),('AnnieMediaDownloader.kt',DOWN,repair.media_downloader),('ExtensionsManager.kt',EXT,repair.extensions),('ScriptStudio.kt',STUDIO,repair.studio),('AnnieBrowser.kt',BROWSER,repair.browser_session)]:
    output=fix(source)
    assert output!=source or name=='DownloadManager.kt', f'{name}: no transformation'
    assert fix(output)==output, f'{name}: not idempotent'
    print('PASS',name,'idempotent')
fixed=repair.browser_session(BROWSER)
assert 'scrollY::$instanceId' in fixed and 'p.all.keys.filter' in fixed
assert 'fun scrollY(context: Context, spec: AnnieBrowserSpec, instanceId: String)' in fixed
assert 'fun saveScroll(context: Context, spec: AnnieBrowserSpec, instanceId: String, scrollY: Int)' in fixed
try:
    repair.archive('unexpected signature')
except repair.RepairError:
    print('PASS unknown source revisions rejected')
else:
    raise AssertionError('unexpected source accepted')
print('PASS seven repair targets validated (fixtures only)')
