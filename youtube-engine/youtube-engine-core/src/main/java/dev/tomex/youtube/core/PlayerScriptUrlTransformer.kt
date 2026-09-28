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
    ): PlayerUrlTransformResult? {
        val normalized = PlayerUrlTransforms.normalizePlayerJavaScriptUrl(playerJavaScriptUrl) ?: return null
        if (normalized != playerJavaScriptUrl || !trustedMediaUrl(mediaUrl)) return null
        if ((signatureParameter == null) != (encryptedSignature == null)) return null

        for (scriptUrl in playerScriptVariants(normalized)) {
            if (discoveryFailed(scriptUrl)) continue
            val script = source.load(scriptUrl) ?: continue
            val candidate = discoverBuilder(scriptUrl, script) ?: continue
            val transformed = runtime.transformUrl(
                playerScript = script,
                candidate = candidate,
                mediaUrl = mediaUrl,
                signatureParameter = signatureParameter,
                encryptedSignature = encryptedSignature
            ) ?: continue
            validateTransform(
                mediaUrl = mediaUrl,
                transformed = transformed,
                signatureParameter = signatureParameter,
                encryptedSignature = encryptedSignature
            )?.let { return it }
        }
        return null
    }

    /**
     * The bootstrap may advertise the IAS bundle while the same immutable player revision also
     * exposes the ES6 bundle. Current revisions can move the URL-builder shape between those
     * variants, so discovery may try the sibling variant for the same player id. No cross-version
     * fallback is allowed.
     */
    private fun playerScriptVariants(normalized: String): List<String> = buildList {
        add(normalized)
        val alternatives = listOf(
            "/player_ias.vflset/" to "/player_es6.vflset/",
            "/player_es6.vflset/" to "/player_ias.vflset/"
        )
        for ((from, to) in alternatives) {
            if (from in normalized) {
                val variant = normalized.replace(from, to)
                if (variant != normalized) add(variant)
            }
        }
    }.distinct().take(2)

    private fun validateTransform(
        mediaUrl: String,
        transformed: String,
        signatureParameter: String?,
        encryptedSignature: String?
    ): PlayerUrlTransformResult? {
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

    private fun discoveryFailed(playerJavaScriptUrl: String): Boolean =
        synchronized(discoveryLock) { failedDiscovery.contains(playerJavaScriptUrl) }

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
