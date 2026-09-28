package dev.tomex.youtube.core

import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.net.URL
import java.net.URLDecoder

data class PlayerUrlTransformResult(
    val url: String,
    val signatureApplied: Boolean,
    val nTransformed: Boolean
)

interface PlayerUrlTransformer {
    suspend fun transform(
        playerJavaScriptUrl: String,
        mediaUrl: String,
        signatureParameter: String? = null,
        encryptedSignature: String? = null
    ): PlayerUrlTransformResult?
}

object NoPlayerUrlTransformer : PlayerUrlTransformer {
    override suspend fun transform(
        playerJavaScriptUrl: String,
        mediaUrl: String,
        signatureParameter: String?,
        encryptedSignature: String?
    ): PlayerUrlTransformResult? = null
}

interface PlayerScriptRuntime {
    suspend fun transformUrl(
        playerScript: String,
        candidate: PlayerScriptUrlBuilderCandidate,
        mediaUrl: String,
        signatureParameter: String?,
        encryptedSignature: String?
    ): String?
}

/**
 * Executes only a size-bounded YouTube player script obtained through [PlayerScriptSource].
 *
 * The runtime has a memory cap, stack cap and hard evaluation timeout. The discovered URL-builder
 * binding is exported structurally; rotating minified names are never hard-coded. Unknown script
 * layouts or missing browser APIs fail closed and allow the static parser path to remain available.
 */
class QuickJsPlayerScriptRuntime(
    private val memoryLimitBytes: Long = 64L * 1024 * 1024,
    private val maxStackBytes: Long = 1024L * 1024,
    private val evaluationTimeoutMs: Long = 1_500,
    private val wallTimeoutMs: Long = 3_000
) : PlayerScriptRuntime {
    init {
        require(memoryLimitBytes in 16L * 1024 * 1024..256L * 1024 * 1024)
        require(maxStackBytes in 256L * 1024..8L * 1024 * 1024)
        require(evaluationTimeoutMs in 100..10_000)
        require(wallTimeoutMs in evaluationTimeoutMs..15_000)
    }

    override suspend fun transformUrl(
        playerScript: String,
        candidate: PlayerScriptUrlBuilderCandidate,
        mediaUrl: String,
        signatureParameter: String?,
        encryptedSignature: String?
    ): String? {
        if (playerScript.isBlank() || playerScript.length > 8 * 1024 * 1024) return null
        if (!trustedMediaUrl(mediaUrl)) return null
        if (signatureParameter != null && !Regex("[A-Za-z0-9_-]{1,64}").matches(signatureParameter)) return null
        if (encryptedSignature != null && (encryptedSignature.isBlank() || encryptedSignature.length > 8192)) return null
        val instrumented = exportBuilder(playerScript, candidate.functionName) ?: return null

        return withContext(Dispatchers.Default) {
            evaluateInstrumented(
                instrumented = instrumented,
                mediaUrl = mediaUrl,
                signatureParameter = signatureParameter,
                encryptedSignature = encryptedSignature,
                stopAfterCapture = true
            ) ?: evaluateInstrumented(
                instrumented = instrumented,
                mediaUrl = mediaUrl,
                signatureParameter = signatureParameter,
                encryptedSignature = encryptedSignature,
                stopAfterCapture = false
            )
        }
    }

    /**
     * First attempt stops as soon as the structurally discovered builder is captured. This keeps
     * pathological player startup code out of the hot path. If the builder closes over helpers
     * assigned later in the player bootstrap, the second bounded attempt continues until normal
     * completion, the first unrelated bootstrap exception, or the same hard QuickJS timeout.
     */
    private suspend fun evaluateInstrumented(
        instrumented: String,
        mediaUrl: String,
        signatureParameter: String?,
        encryptedSignature: String?,
        stopAfterCapture: Boolean
    ): String? {
        val program = browserStubs(stopAfterCapture) +
            "\ntry{\n" + instrumented + "\n}catch(__ytEngineBootstrapError){}\n" +
            invocation(mediaUrl, signatureParameter, encryptedSignature)
        val quickJs = try {
            QuickJs.create(Dispatchers.Default)
        } catch (_: Exception) {
            return null
        }
        return try {
            quickJs.memoryLimit = memoryLimitBytes
            quickJs.maxStackSize = maxStackBytes
            quickJs.evaluationTimeoutMillis = evaluationTimeoutMs
            withTimeout(wallTimeoutMs) {
                quickJs.evaluate<String?>(program, filename = "youtube-player.js")
            }
        } catch (_: TimeoutCancellationException) {
            null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } finally {
            quickJs.close()
        }
    }

    private fun exportBuilder(script: String, functionName: String): String? {
        val dollar = 36.toChar()
        val identifier = "[A-Za-z_" + dollar + "][A-Za-z0-9_" + dollar + "]*"
        if (!Regex(identifier).matches(functionName)) return null
        val name = Regex.escape(functionName)

        val assignmentPattern = Regex(
            "(?<![A-Za-z0-9_" + dollar + "])" + name + "\\s*=\\s*function\\s*\\("
        )
        assignmentPattern.find(script)?.let { assignment ->
            val value = assignment.value
            val equalsAt = value.indexOf('=')
            if (equalsAt < 0) return null
            val bodyOpen = findFunctionBodyOpenBrace(script, assignment.range.last, 2 * 1024) ?: return null
            val bodyClose = findMatchingBrace(script, bodyOpen, 128 * 1024) ?: return null
            val prefix = value.substring(0, equalsAt + 1)
            return script.substring(0, assignment.range.first) +
                prefix + "globalThis.__ytEngineCapture(function(" +
                script.substring(assignment.range.last + 1, bodyClose + 1) +
                ")" +
                script.substring(bodyClose + 1)
        }

        val declaration = Regex("\\bfunction\\s+" + name + "\\s*\\(").find(script) ?: return null
        val before = script.substring(
            maxOf(0, declaration.range.first - 48),
            declaration.range.first
        ).trimEnd()
        if (before.endsWith("=") || before.endsWith(":") || before.endsWith(",") ||
            before.endsWith("(") || before.endsWith("return")
        ) return null

        val bodyOpen = findFunctionBodyOpenBrace(script, declaration.range.last, 2 * 1024) ?: return null
        val bodyClose = findMatchingBrace(script, bodyOpen, 128 * 1024) ?: return null
        return script.substring(0, bodyClose + 1) +
            ";globalThis.__ytEngineCapture(" + functionName + ");" +
            script.substring(bodyClose + 1)
    }

    private fun findFunctionBodyOpenBrace(source: String, openParen: Int, maxChars: Int): Int? {
        if (openParen !in source.indices || source[openParen] != '(') return null
        val limit = minOf(source.length, openParen + maxChars + 1)
        var depth = 0
        var quote: Char? = null
        var escaped = false
        var index = openParen
        while (index < limit) {
            val value = source[index]
            if (quote != null) {
                if (escaped) escaped = false
                else if (value.code == 92) escaped = true
                else if (value == quote) quote = null
                index++
                continue
            }
            if (value.code == 39 || value.code == 34 || value.code == 96) {
                quote = value
                index++
                continue
            }
            when (value) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) {
                        var cursor = index + 1
                        while (cursor < limit && source[cursor].isWhitespace()) cursor++
                        return cursor.takeIf { it < source.length && source[it] == '{' }
                    }
                    if (depth < 0) return null
                }
            }
            index++
        }
        return null
    }

    private fun findMatchingBrace(source: String, openBrace: Int, maxChars: Int): Int? {
        if (openBrace !in source.indices || source[openBrace] != '{') return null
        val limit = minOf(source.length, openBrace + maxChars + 1)
        var depth = 0
        var quote: Char? = null
        var escaped = false
        var lineComment = false
        var blockComment = false
        var index = openBrace
        while (index < limit) {
            val value = source[index]
            val next = source.getOrNull(index + 1)
            if (lineComment) {
                if (value.code == 10 || value.code == 13) lineComment = false
                index++
                continue
            }
            if (blockComment) {
                if (value == '*' && next == '/') {
                    blockComment = false
                    index += 2
                } else index++
                continue
            }
            if (quote != null) {
                if (escaped) escaped = false
                else if (value.code == 92) escaped = true
                else if (value == quote) quote = null
                index++
                continue
            }
            if (value == '/' && next == '/') {
                lineComment = true
                index += 2
                continue
            }
            if (value == '/' && next == '*') {
                blockComment = true
                index += 2
                continue
            }
            if (value.code == 39 || value.code == 34 || value.code == 96) {
                quote = value
                index++
                continue
            }
            if (value == '{') depth++
            if (value == '}') {
                depth--
                if (depth == 0) return index
                if (depth < 0) return null
            }
            index++
        }
        return null
    }

    private fun invocation(
        mediaUrl: String,
        signatureParameter: String?,
        encryptedSignature: String?
    ): String {
        val url = JSONObject.quote(mediaUrl)
        val sp = signatureParameter?.let { JSONObject.quote(it) } ?: "null"
        val signature = encryptedSignature?.let { JSONObject.quote(it) } ?: "null"
        return """
;
(function(){
    const __ytInput=$url;
    const __ytSp=$sp;
    const __ytEncrypted=$signature;
    if(typeof globalThis.__ytEngineUrlBuilder!=="function") return null;
    let __ytValue;
    try {
        __ytValue=__ytEncrypted===null
            ? globalThis.__ytEngineUrlBuilder(__ytInput)
            : globalThis.__ytEngineUrlBuilder(__ytInput,__ytSp||"signature",__ytEncrypted);
    } catch(e) {
        return null;
    }
    function __ytRunUrlTransforms(value){
        if(value===null || value===undefined || typeof value==="string") return;
        let proto=null;
        try { proto=Object.getPrototypeOf(value); } catch(e) {}
        if(!proto) return;
        let names=[];
        try { names=Object.getOwnPropertyNames(proto); } catch(e) { return; }
        const blacklist={constructor:1,clone:1,set:1,get:1,toString:1,valueOf:1,toJSON:1};
        let invoked=0;
        for(const name of names){
            if(invoked>=96 || blacklist[name]) continue;
            let fn=null;
            try { fn=value[name]; } catch(e) { continue; }
            if(typeof fn!=="function") continue;
            try {
                fn.call(value);
                invoked++;
            } catch(e) {}
        }
    }
    function __ytAsUrl(value){
        if(typeof value==="string" && value.indexOf("https://")===0) return value;
        if(value===null || value===undefined) return null;
        try {
            if(typeof value.toString==="function"){
                const direct=value.toString();
                if(typeof direct==="string" && direct.indexOf("https://")===0) return direct;
            }
        } catch(e) {}
        let proto=null;
        try { proto=Object.getPrototypeOf(value); } catch(e) {}
        if(proto){
            let names=[];
            try { names=Object.getOwnPropertyNames(proto); } catch(e) {}
            for(const name of names.slice(0,96)){
                if(name==="constructor" || name==="clone" || name==="set" || name==="get") continue;
                let fn=null;
                try { fn=value[name]; } catch(e) { continue; }
                if(typeof fn!=="function") continue;
                try {
                    const candidate=fn.call(value);
                    if(typeof candidate==="string" && candidate.indexOf("https://")===0) return candidate;
                } catch(e) {}
            }
        }
        return null;
    }
    __ytRunUrlTransforms(__ytValue);
    return __ytAsUrl(__ytValue);
})()
""".trimIndent()
    }

    private fun browserStubs(stopAfterCapture: Boolean): String = """
(function(){
    const g=globalThis;
    if(typeof g.window==="undefined") g.window=g;
    g.__ytEngineStop={};
    g.__ytEngineStopAfterCapture=$stopAfterCapture;
    g.__ytEngineCapture=function(fn){
        if(typeof fn!=="function") throw new Error("invalid engine URL builder");
        g.__ytEngineUrlBuilder=fn;
        if(g.__ytEngineStopAfterCapture) throw g.__ytEngineStop;
        return fn;
    };
    if(typeof g.self==="undefined") g.self=g;
    if(typeof g.global==="undefined") g.global=g;
    if(typeof g.navigator==="undefined") g.navigator={userAgent:"Mozilla/5.0",language:"en-US",languages:["en-US","en"],platform:"Linux x86_64"};
    if(typeof g.location==="undefined") g.location={href:"https://www.youtube.com/",protocol:"https:",host:"www.youtube.com",hostname:"www.youtube.com",origin:"https://www.youtube.com",pathname:"/",search:"",hash:""};
    if(typeof g.console==="undefined") g.console={log:function(){},warn:function(){},error:function(){},debug:function(){}};
    if(typeof g.performance==="undefined") g.performance={now:function(){return 0},timeOrigin:0};
    if(typeof g.requestAnimationFrame==="undefined") g.requestAnimationFrame=function(){return 0};
    if(typeof g.cancelAnimationFrame==="undefined") g.cancelAnimationFrame=function(){};
    if(typeof g.setTimeout==="undefined") g.setTimeout=function(){return 0};
    if(typeof g.clearTimeout==="undefined") g.clearTimeout=function(){};
    if(typeof g.setInterval==="undefined") g.setInterval=function(){return 0};
    if(typeof g.clearInterval==="undefined") g.clearInterval=function(){};
    if(typeof g.Image==="undefined") g.Image=function(){return {}};
    if(typeof g.MutationObserver==="undefined") g.MutationObserver=function(){this.observe=function(){};this.disconnect=function(){}};
    if(typeof g.localStorage==="undefined") g.localStorage={getItem:function(){return null},setItem:function(){},removeItem:function(){},clear:function(){}};
    if(typeof g.sessionStorage==="undefined") g.sessionStorage=g.localStorage;
    if(typeof g.document==="undefined") {
        const node=function(){return {style:{},children:[],appendChild:function(){},removeChild:function(){},remove:function(){},setAttribute:function(){},getAttribute:function(){return null},addEventListener:function(){},removeEventListener:function(){},querySelector:function(){return null},querySelectorAll:function(){return []},getContext:function(){return null}}};
        g.document={readyState:"complete",body:node(),head:node(),documentElement:node(),createElement:node,createTextNode:function(){return node()},getElementById:function(){return null},getElementsByTagName:function(){return []},querySelector:function(){return null},querySelectorAll:function(){return []},addEventListener:function(){},removeEventListener:function(){}};
    }
})();
""".trimIndent() + "\n"

    private fun trustedMediaUrl(value: String): Boolean {
        val url = runCatching { URL(value) }.getOrNull() ?: return false
        return url.protocol.equals("https", ignoreCase = true) && url.userInfo == null
    }
}

/**
 * Unified current-player URL transform. Modern player revisions can perform signature and n
 * rewriting through the same URL-builder path, so this is attempted before the legacy independent
 * token parsers. Every output is constrained to the same HTTPS media resource and is still
 * unverified until CDN transport succeeds.
 */
class PlayerScriptUrlTransformer(
    private val source: PlayerScriptSource = CachedPlayerScriptSource(),
    private val runtime: PlayerScriptRuntime = QuickJsPlayerScriptRuntime(),
    private val maxPlayerRevisions: Int = 4
) : PlayerUrlTransformer {
    init { require(maxPlayerRevisions in 1..16) }

    private val discoveryLock = Any()
    private val discoveredBuilders = object :
        LinkedHashMap<String, PlayerScriptUrlBuilderCandidate>(8, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, PlayerScriptUrlBuilderCandidate>?
        ): Boolean = size > maxPlayerRevisions
    }
    private val failedDiscovery = LinkedHashSet<String>()
    override suspend fun transform(
        playerJavaScriptUrl: String,
        mediaUrl: String,
        signatureParameter: String?,
        encryptedSignature: String?
    ): PlayerUrlTransformResult? {
        val normalized = PlayerUrlTransforms.normalizePlayerJavaScriptUrl(playerJavaScriptUrl) ?: return null
        if (normalized != playerJavaScriptUrl || !trustedMediaUrl(mediaUrl)) return null
        if ((signatureParameter == null) != (encryptedSignature == null)) return null
        val script = source.load(normalized) ?: return null
        val candidate = discoverBuilder(normalized, script) ?: return null
        val transformed = runtime.transformUrl(
            playerScript = script,
            candidate = candidate,
            mediaUrl = mediaUrl,
            signatureParameter = signatureParameter,
            encryptedSignature = encryptedSignature
        ) ?: return null
        if (!sameMediaResource(mediaUrl, transformed)) return null

        val originalN = PlayerUrlTransforms.extractN(mediaUrl)
        val transformedN = PlayerUrlTransforms.extractN(transformed)
        val nChanged = originalN != null && transformedN != null && originalN != transformedN
        val signatureApplied = if (encryptedSignature != null && signatureParameter != null) {
            queryParameter(transformed, signatureParameter)
                ?.takeIf { it.isNotBlank() && it != encryptedSignature } != null
        } else false

        if (encryptedSignature != null && !signatureApplied) return null
        if (encryptedSignature == null && originalN != null && !nChanged) return null
        if (transformed == mediaUrl) return null
        return PlayerUrlTransformResult(transformed, signatureApplied, nChanged)
    }

    private fun discoverBuilder(
        playerJavaScriptUrl: String,
        script: String
    ): PlayerScriptUrlBuilderCandidate? {
        synchronized(discoveryLock) {
            discoveredBuilders[playerJavaScriptUrl]?.let { return it }
            if (failedDiscovery.contains(playerJavaScriptUrl)) return null
        }
        val candidate = PlayerScriptNParameterParser.inspect(script).urlBuilderCandidates.singleOrNull()
        synchronized(discoveryLock) {
            if (candidate == null) {
                failedDiscovery += playerJavaScriptUrl
                while (failedDiscovery.size > maxPlayerRevisions) failedDiscovery.remove(failedDiscovery.first())
            } else {
                failedDiscovery.remove(playerJavaScriptUrl)
                discoveredBuilders[playerJavaScriptUrl] = candidate
            }
        }
        return candidate
    }

    private fun sameMediaResource(before: String, after: String): Boolean {
        val original = runCatching { URL(before) }.getOrNull() ?: return false
        val changed = runCatching { URL(after) }.getOrNull() ?: return false
        if (!changed.protocol.equals("https", ignoreCase = true) || changed.userInfo != null) return false
        return original.host.equals(changed.host, ignoreCase = true) &&
            effectivePort(original) == effectivePort(changed) &&
            original.path == changed.path
    }

    private fun effectivePort(url: URL): Int =
        if (url.port >= 0) url.port else url.defaultPort

    private fun queryParameter(url: String, name: String): String? {
        val query = runCatching { URL(url).query }.getOrNull() ?: return null
        val values = query.split('&').mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator < 0) return@mapNotNull null
            val key = decode(part.substring(0, separator)) ?: return@mapNotNull null
            if (key != name) return@mapNotNull null
            decode(part.substring(separator + 1))
        }
        return values.singleOrNull()
    }

    private fun decode(value: String): String? =
        runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrNull()

    private fun trustedMediaUrl(value: String): Boolean {
        val url = runCatching { URL(value) }.getOrNull() ?: return false
        return url.protocol.equals("https", ignoreCase = true) && url.userInfo == null
    }
}
