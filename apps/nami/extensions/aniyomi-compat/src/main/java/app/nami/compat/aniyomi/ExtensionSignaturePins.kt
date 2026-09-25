package app.nami.compat.aniyomi

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import java.security.MessageDigest

internal enum class ExtensionSignerDecision {
    FIRST_SEEN_PINNED,
    TRUSTED,
    REJECTED,
    UNSIGNED,
}

internal object ExtensionSignerPolicy {
    fun decide(
        pinnedFingerprints: Set<String>,
        currentFingerprints: Set<String>,
    ): ExtensionSignerDecision = when {
        currentFingerprints.isEmpty() -> ExtensionSignerDecision.UNSIGNED
        pinnedFingerprints.isEmpty() -> ExtensionSignerDecision.FIRST_SEEN_PINNED
        currentFingerprints.any { it in pinnedFingerprints } -> ExtensionSignerDecision.TRUSTED
        else -> ExtensionSignerDecision.REJECTED
    }
}

/**
 * Trust-on-first-use signature pinning for already-installed extension APKs.
 *
 * Android normally protects package updates with signer continuity. Keeping our own pin also
 * protects Nami after an extension was uninstalled/reinstalled under the same package name.
 */
internal class ExtensionSignaturePins(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun verify(packageInfo: PackageInfo): ExtensionSignerDecision {
        val current = signingFingerprints(packageInfo)
        val pinned = preferences
            .getStringSet(packageInfo.packageName, emptySet())
            .orEmpty()
            .toSet()

        val decision = ExtensionSignerPolicy.decide(pinned, current)
        if (decision == ExtensionSignerDecision.FIRST_SEEN_PINNED) {
            preferences.edit()
                .putStringSet(packageInfo.packageName, current)
                .apply()
        }
        return decision
    }

    fun forget(packageName: String) {
        preferences.edit().remove(packageName).apply()
    }

    internal fun signingFingerprints(packageInfo: PackageInfo): Set<String> {
        @Suppress("DEPRECATION")
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            packageInfo.signatures.orEmpty()
        }

        return signatures
            .map { signature -> sha256(signature.toByteArray()) }
            .toSet()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    companion object {
        private const val PREFS_NAME = "nami_extension_signer_pins"
    }
}
