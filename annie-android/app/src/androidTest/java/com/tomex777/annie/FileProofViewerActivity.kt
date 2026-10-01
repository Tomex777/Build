package com.tomex777.annie

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import java.security.MessageDigest

/** A separate test-APK recipient verifies that the granted URI really supplies all bytes. */
class FileProofViewerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = runCatching {
            val uri = requireNotNull(intent.data)
            require(uri.scheme == "content")
            val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            "File opened safely\n${bytes.size} bytes\n$hash"
        }.getOrElse { "File permission failed: ${it.javaClass.simpleName}" }
        setContentView(TextView(this).apply { text = result; textSize = 18f; setPadding(24, 60, 24, 24) })
    }
}
