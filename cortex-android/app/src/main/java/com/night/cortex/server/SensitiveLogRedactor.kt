package com.night.cortex.server

/**
 * Client-side, defense-in-depth sanitization for journal lines from older agents.
 * Never persist/display raw WhatsApp Signal session keys or encrypted metadata.
 */
internal object SensitiveLogRedactor {
    private val binary = Regex("""<Buffer\s+(?:[\da-fA-F]{2}\s*)+>""")
    private val credential = Regex("""(?i)(\b(?:bearer|authorization|access[_-]?token|refresh[_-]?token|api[_-]?key|password|secret|pairing[_-]?code)\b\s*[:=]\s*)([^\s,}\]]+)""")
    private val signalField = Regex("""(?i)\b(?:baseKey|rootKey|chainKey|privKey|pubKey|senderKey|identityKey|signedPreKey|pendingPreKey|ephemeralKeyPair|remoteIdentityKey|sessionKey|messageKeys|macKey|mediaKey|privateKey|publicKey)\b\s*[:=]""")
    private val longHex = Regex("""(?i)\b(?:[0-9a-f]{2}[ :]){14,}[0-9a-f]{2}\b""")

    fun sanitize(line: String): String {
        if (signalField.containsMatchIn(line)) {
            val prefix = line.substringBefore(':').take(130)
            return "$prefix: [REDACTED — SESSION MATERIAL]"
        }
        return longHex.replace(
            credential.replace(binary.replace(line, "<Buffer [REDACTED]>")) {
                "${it.groupValues[1]}[REDACTED]"
            },
            "[REDACTED HEX]"
        ).take(3000)
    }
}
