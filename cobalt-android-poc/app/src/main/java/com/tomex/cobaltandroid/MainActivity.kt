package com.tomex.cobaltandroid

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(TextView(this).apply {
            textSize = 18f
            setPadding(40, 80, 40, 40)
            text = runCobaltClassProbe()
        })
    }

    private fun runCobaltClassProbe(): String {
        val className = "com.github.auties00.cobalt.client.WhatsAppClient"
        return try {
            // Avoid compile-time references to Java-25 Cobalt types for this first D8 test.
            Class.forName(className, false, classLoader)
            "Cobalt packaged successfully.\n$className is visible to Android."
        } catch (error: Throwable) {
            buildString {
                appendLine("Cobalt runtime probe failed.")
                appendLine(error::class.java.name)
                append(error.message ?: "No message")
            }
        }
    }
}
