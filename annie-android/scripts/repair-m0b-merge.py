#!/usr/bin/env python3
"""Idempotent repairs for the Annie M0b overlay applied over Tomex777/Build.

Run from ANY directory after overlaying annie-m0b-full-merged.zip on a complete
checkout of branch annie-android-ci. Does not apply the stale fuzz patch.
"""
from __future__ import annotations
import argparse
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/com/tomex777/annie'

class RepairError(RuntimeError): pass

def once(src: str, old: str, new: str, label: str) -> str:
    if old in src:
        if src.count(old) != 1:
            raise RepairError(f'{label}: expected exactly one anchor, found {src.count(old)}')
        return src.replace(old, new, 1)
    if new in src:  # rerun is safe
        return src
    raise RepairError(f'{label}: unknown source revision; refusing to guess')

def section(src: str, start: str, end: str, replacement: str, label: str) -> str:
    i = src.find(start)
    j = src.find(end, i + len(start)) if i >= 0 else -1
    if i < 0 or j < 0 or src.count(start) != 1:
        raise RepairError(f'{label}: cannot uniquely find section')
    return src[:i] + replacement + src[j:]

def package(src: str) -> str:
    src = src.replace("    /** Compatibility range from requires.annie. */\n", "")
    src = src.replace("    /** Declared network hosts; actual HTTP enforcement is a separate feature. */\n", "")
    # The Oct 7 base already has Map/Set requires/network declarations. The old patch
    # inserts the new String/List ones without removing them. Replace the old block
    # and drop any accidentally fuzz-inserted duplicate block.
    src = re.sub(r'(?m)^    val requires: Map<String, String> = emptyMap\(\),\n', '', src)
    src = re.sub(r'(?m)^    val networkHosts: Set<String> = emptySet\(\),\n', '', src)
    src = re.sub(r'(?m)^    val publisher: AnniePackagePublisher\? = null,\n', '', src)
    src = re.sub(r'(?m)^    /\*\* requires\.annie:[^\n]*\*/\n', '', src)
    src = re.sub(r'(?m)^    val requires: String\? = null,\n', '', src)
    src = re.sub(r'(?m)^    /\*\* network\.hosts:[^\n]*\*/\n', '', src)
    src = re.sub(r'(?m)^    val networkHosts: List<String> = emptyList\(\),\n', '', src)
    anchor = '    val entryPoint: String,\n'
    src = once(src, anchor, anchor +
        '    /** Compatibility range from requires.annie. */\n'
        '    val requires: String? = null,\n'
        '    val publisher: AnniePackagePublisher? = null,\n'
        '    /** Declared network hosts; actual HTTP enforcement is a separate feature. */\n'
        '    val networkHosts: List<String> = emptyList(),\n', 'manifest fields')
    # Consolidate duplicate publisher declaration; preserve multiline declaration.
    one_line_pub = "internal data class AnniePackagePublisher(val id: String, val name: String)\n"
    if src.count("internal data class AnniePackagePublisher") > 1:
        src = src.replace(one_line_pub, "")
    declarations = re.findall(r'internal data class AnniePackagePublisher\s*\(', src)
    if len(declarations) != 1:
        raise RepairError(f'expected one AnniePackagePublisher declaration, got {len(declarations)}')
    prop = '    val validationMode: ValidationMode get() = ApiCompatibility.modeFor(apiVersion) ?: ValidationMode.LEGACY\n'
    if prop not in src:
        src = once(src, ') {\n    companion object {', ') {\n' + prop + '\n    companion object {', 'validation mode')
    for field in ('requires: String?', 'networkHosts: List<String>', 'publisher: AnniePackagePublisher?'):
        if src.count('val '+field) != 1: raise RepairError(f'duplicate/missing {field}')
    return src

MANIFEST_PARSE = '''        val apiVersion = json.optString("apiVersion").ifBlank { AnniePackageManifest.CURRENT_API_VERSION }
        // Validation and version-range checks share one contract; do not pre-empt
        // them with the historical exact-equality or independent regex checks.
        val requiresObject = manifestObject(json, "requires")
        if (requiresObject != null) {
            require(requiresObject.length() <= 1 && requiresObject.has("annie")) {
                "Only requires.annie is supported"
            }
        }
        val requiresAnnie = requiresObject?.let { row ->
            require(row.opt("annie") is String) { "requires.annie must be a version range string" }
            row.getString("annie").trim()
        }
        ApiCompatibility.check(apiVersion, requiresAnnie, AnniePackageManifest.CURRENT_API_VERSION.toInt())
            ?.let { throw IllegalArgumentException(it) }
        val publisher = manifestObject(json, "publisher")?.let { row ->
            val id = row.optString("id").trim()
            val name = row.optString("name").trim()
            ManifestIdentity.validatePublisher(id.ifBlank { null }, name)
                ?.let { throw IllegalArgumentException(it) }
            AnniePackagePublisher(id, name)
        }
        val networkHosts = manifestObject(json, "network")?.let { row ->
            val hosts = manifestArray(row, "hosts")
                ?: throw IllegalArgumentException("network.hosts must be an array")
            List(hosts.length()) { index ->
                require(hosts.opt(index) is String) { "network.hosts must contain only strings" }
                hosts.getString(index).trim()
            }
        }.orEmpty()
        ManifestIdentity.validateHosts(networkHosts)?.let { throw IllegalArgumentException(it) }
'''

def archive(src: str) -> str:
    src = src.replace('    private fun decodeManifest(', '    internal fun decodeManifest(', 1)
    src = src.replace('    private fun encodeManifest(', '    internal fun encodeManifest(', 1)
    src = section(src,
        '        val apiVersion = json.optString("apiVersion")',
        '        val entryPoint = json.optString("entryPoint")', MANIFEST_PARSE, 'archive parser')
    # Drop old duplicate requirements/publisher/network fields in return invocation,
    # including any values inserted by a partial fuzz patch.
    start = '            entryPoint = entryPoint,\n'
    end = '            permissions = permissions,\n'
    src = section(src, start, end,
        start + '            requires = requiresAnnie,\n'
              '            publisher = publisher,\n'
              '            networkHosts = networkHosts,\n', 'archive return')
    start = '        .put("requires", JSONObject().apply'
    end = '        .put("permissions", JSONArray(manifest.permissions.toList()))'
    if start in src:
        src = section(src, start, end,
            '        .apply {\n'
            '            manifest.requires?.let { put("requires", JSONObject().put("annie", it)) }\n'
            '            manifest.publisher?.let { put("publisher", JSONObject().put("id", it.id).put("name", it.name)) }\n'
            '            if (manifest.networkHosts.isNotEmpty()) put("network", JSONObject().put("hosts", JSONArray(manifest.networkHosts)))\n'
            '        }\n', 'archive serializer')
    elif 'manifest.requires?.let { put("requires"' not in src:
        raise RepairError('archive serializer: no known shape')
    # A prior fuzzy merge may also have appended an .apply at the bottom of encodeManifest.
    legacy_dup = '''        .apply {
            manifest.requires?.let { put("requires", JSONObject().put("annie", it)) }
            manifest.publisher?.let { put("publisher", JSONObject().put("id", it.id).put("name", it.name)) }
            if (manifest.networkHosts.isNotEmpty()) put("network", JSONObject().put("hosts", JSONArray(manifest.networkHosts)))
        }
'''
    if src.count(legacy_dup) > 1:
        first = src.find(legacy_dup)
        src = src[:first+len(legacy_dup)] + src[first+len(legacy_dup):].replace(legacy_dup, '')
    if 'require(apiVersion == AnniePackageManifest.CURRENT_API_VERSION)' in src:
        raise RepairError('old exact-equality gate survived')
    return src

def download_manager(src: str) -> str:
    # On the actual Oct 7 branch all three rejected hunks are ALREADY present.
    # Ensure they are complete, inserting just the missing pieces on older checkouts.
    field = '    val completionActionJson: String = "",\n    val completionChatId: String? = null,\n'
    if field not in src:
        src = once(src, '    val refreshPayloadJson: String = "{}",\n',
            '    val refreshPayloadJson: String = "{}",\n' + field, 'download fields')
    if 'completionActionJson = json.optString("completionActionJson")' not in src:
        src = once(src, '                        refreshPayloadJson = json.optString("refreshPayloadJson", "{}"),\n',
            '                        refreshPayloadJson = json.optString("refreshPayloadJson", "{}"),\n'
            '                        completionActionJson = json.optString("completionActionJson"),\n'
            '                        completionChatId = json.optString("completionChatId").takeIf { it.isNotBlank() },\n', 'download load')
    if '.put("completionActionJson", item.completionActionJson)' not in src:
        src = once(src, '                    .put("refreshPayloadJson", item.refreshPayloadJson)\n',
            '                    .put("refreshPayloadJson", item.refreshPayloadJson)\n'
            '                    .put("completionActionJson", item.completionActionJson)\n'
            '                    .put("completionChatId", item.completionChatId ?: "")\n', 'download save')
    for s in ['val completionActionJson: String', 'completionActionJson = json.optString', '.put("completionActionJson"']:
        if src.count(s) != 1: raise RepairError('download item fields missing or duplicated: '+s)
    return src

def media_downloader(src: str) -> str:
    if 'DownloadCompletionDispatcher.enqueue(context, completed)' in src:
        return src
    start = '        publish(\n            item.copy(\n                state = DownloadState.COMPLETE,'
    end = '\n    private fun finalFile(item: DownloadItem'
    i = src.find(start)
    j = src.find(end, i) if i >= 0 else -1
    if i < 0 or j < 0: raise RepairError('cannot locate download COMPLETE publish')
    segment = src[i:j]
    ending = '''        val completed = item.copy(
            state = DownloadState.COMPLETE,
            progress = 1f,
            bytesDone = finalFile.length(),
            bytesTotal = finalFile.length(),
            localPath = finalFile.absolutePath,
            failureReason = "",
            sourceMimeType = mimeType ?: item.sourceMimeType,
        )
        publish(completed)
        // Only completed downloads dispatch actions, never failed or cancelled ones.
        DownloadCompletionDispatcher.enqueue(context, completed)
    }
'''
    if not segment.rstrip().endswith(')\n    }'):
        raise RepairError('download completion code moved; review manually')
    return src[:i] + ending + src[j:]

def extensions(src: str) -> str:
    if 'commandWarnings: (ScriptProject) -> List<String>' not in src:
        src = once(src, '    grantedPermissions: (ScriptProject) -> Set<String> = { emptySet() },\n',
            '    grantedPermissions: (ScriptProject) -> Set<String> = { emptySet() },\n'
            '    commandWarnings: (ScriptProject) -> List<String> = { emptyList() },\n', 'extensions callback')
    old = 'ExtensionProjectCard(project, grantedPermissions(project), onToggle, onConfigure, onOpenStudio)'
    new = 'ExtensionProjectCard(project, grantedPermissions(project), commandWarnings(project), onToggle, onConfigure, onOpenStudio)'
    src = once(src, old, new, 'extensions card call')
    if '    commandWarnings: List<String>,\n' not in src:
        src = once(src, 'private fun ExtensionProjectCard(\n    project: ScriptProject,\n    grantedPermissions: Set<String>,\n',
            'private fun ExtensionProjectCard(\n    project: ScriptProject,\n    grantedPermissions: Set<String>,\n    commandWarnings: List<String>,\n', 'extensions card parameter')
    labels = {
        'DOWNLOADS_START_PERMISSION': 'Start downloads',
        'DOWNLOADS_CONTROL_PERMISSION': 'Manage its own downloads',
        'MESSAGES_POST_PERMISSION': 'Post and update chat messages',
    }
    for key, label in labels.items():
        if f'    {key} ->' not in src:
            src = once(src, '    else -> if (permission.startsWith("service:")) {',
                f'    {key} -> "{label}"\n    else -> if (permission.startsWith("service:")) {{', 'permission label')
    if 'extension_command_warning_' not in src:
        src = once(src, '                if (project.manifest.permissions.isNotEmpty()) {',
            '                commandWarnings.forEachIndexed { index, warning ->\n'
            '                    Text("⚠ $warning", color = Color(0xFFFFC857), fontSize = 10.sp,\n'
            '                        modifier = Modifier.testTag("extension_command_warning_${project.id}_$index"))\n'
            '                }\n'
            '                if (project.manifest.networkHosts.isNotEmpty()) {\n'
            '                    Text("Reach: " + project.manifest.networkHosts.joinToString(", "),\n'
            '                        color = ExtensionsMuted, fontSize = 10.sp, maxLines = 2,\n'
            '                        overflow = TextOverflow.Ellipsis,\n'
            '                        modifier = Modifier.testTag("extension_hosts_${project.id}"))\n'
            '                }\n'
            '                if (project.manifest.permissions.isNotEmpty()) {', 'extensions indicators')
    return src

def browser_session(src: str) -> str:
    """Repair Oct 7 store to match M0b's per-WebView instance scroll API.

    A browser session identifies a cookie profile; different bubbles sharing that
    profile must not share their stored scroll position. Never read an old global
    scrollY value as an instance-specific position.
    """
    old = '''    fun scrollY(context: Context, spec: AnnieBrowserSpec): Int =
        prefs(context).getInt(key(spec.sanitized().sessionId, "scrollY"), 0).coerceAtLeast(0)
'''
    extra = '''
    /** Scroll positions belong to a browser tab instance, not the shared cookie/session profile. */
    fun saveScroll(context: Context, spec: AnnieBrowserSpec, instanceId: String, scrollY: Int) {
        require(instanceId.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}"))) { "Invalid browser instance id" }
        val sessionId = spec.sanitized().sessionId
        prefs(context).edit().putInt(key(sessionId, "scrollY::$instanceId"), scrollY.coerceAtLeast(0)).apply()
    }

    /** A tab created for the first time starts at the top; never borrows another tab's position. */
    fun scrollY(context: Context, spec: AnnieBrowserSpec, instanceId: String): Int {
        require(instanceId.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}"))) { "Invalid browser instance id" }
        return prefs(context).getInt(key(spec.sanitized().sessionId, "scrollY::$instanceId"), 0).coerceAtLeast(0)
    }
'''
    if 'fun saveScroll(context: Context, spec: AnnieBrowserSpec, instanceId: String,' not in src:
        src = once(src, old, old + extra, 'per-tab browser scroll persistence')
    if '"scrollY::$instanceId"' not in src:
        raise RepairError('browser store still uses shared session scroll for tabs')
    # Erase per-tab positions when the session is explicitly cleared; otherwise
    # a later instance reusing an id can recover stale layout state.
    old_clear = '''        p.edit().remove(key(sessionId, "url")).remove(key(sessionId, "spec"))
            .remove(key(sessionId, "state")).remove(key(sessionId, "verifiedAt")).remove(key(sessionId, "scrollY")).apply()
'''
    new_clear = '''        val edit = p.edit().remove(key(sessionId, "url")).remove(key(sessionId, "spec"))
            .remove(key(sessionId, "state")).remove(key(sessionId, "verifiedAt")).remove(key(sessionId, "scrollY"))
        p.all.keys.filter { it.startsWith(key(sessionId, "scrollY::")) }.forEach { edit.remove(it) }
        edit.apply()
'''
    return once(src, old_clear, new_clear, 'per-tab browser scroll cleanup')

def studio(src: str) -> str:
    # Sora's standard deleteText() is unreliable for Select-All via some IMEs.
    # Use the Content.replace(start,end,"") API already used by Replace All.
    old = '''                if (!isTextSelected()) return false
                deleteText()
                notifyIMEExternalCursorChange()'''
    new = '''                if (!isTextSelected()) return false
                val cursor = text.cursor
                val start = text.getCharIndex(cursor.leftLine, cursor.leftColumn)
                val end = text.getCharIndex(cursor.rightLine, cursor.rightColumn)
                if (start >= end) return false
                text.replace(start, end, "")
                notifyIMEExternalCursorChange()'''
    return once(src, old, new, 'studio select-all delete')

STEPS = [
    ('AnniePackage.kt', package),
    ('AnniePackageArchive.kt', archive),
    ('DownloadManager.kt', download_manager),
    ('AnnieMediaDownloader.kt', media_downloader),
    ('ExtensionsManager.kt', extensions),
    ('ScriptStudio.kt', studio),
    ('AnnieBrowser.kt', browser_session),
]

def main():
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument('--check', action='store_true', help='validate files without modifying them')
    args = cli.parse_args()
    originals, staged = {}, {}
    try:
        for name, fix in STEPS:
            path = JAVA / name
            if not path.is_file():
                raise RepairError(f'Missing {path}. This ZIP is an overlay, not a full repo; start with a checkout.')
            originals[path] = path.read_text()
            staged[path] = fix(originals[path])
        if not (JAVA/'ApiCompatibility.kt').is_file():
            raise RepairError('ApiCompatibility.kt missing: overlay the supplied ZIP first')
        # Check no duplicate declaration/unsupported legacy parser remains.
        p, a = staged[JAVA/'AnniePackage.kt'], staged[JAVA/'AnniePackageArchive.kt']
        assert p.count('internal data class AnniePackagePublisher') == 1
        assert 'ApiCompatibility.check(apiVersion, requiresAnnie' in a
        assert 'ManifestIdentity.validateHosts(networkHosts)' in a
    except Exception as exc:
        print('FAILED (no files changed):', str(exc))
        raise SystemExit(1)
    changes = [p for p in originals if staged[p] != originals[p]]
    for p in originals:
        print(('WOULD UPDATE' if args.check and p in changes else 'UPDATED' if p in changes else 'OK'), p.name)
    if not args.check:
        for p in changes: p.write_text(staged[p])
    print(f'Repair validation passed; {len(changes)} file(s) {"would change" if args.check else "changed"}.')

if __name__ == '__main__': main()
