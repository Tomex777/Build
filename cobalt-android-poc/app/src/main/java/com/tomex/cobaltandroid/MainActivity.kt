package com.tomex.cobaltandroid

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView

class MainActivity : Activity() {
    private val tag = "CobaltPOC"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val output = TextView(this).apply {
            textSize = 16f
            setPadding(32, 64, 32, 32)
            text = "Starting Cobalt Android runtime probe…"
        }
        setContentView(output)

        Thread {
            val report = runProbe()
            Log.i(tag, report)
            runOnUiThread { output.text = report }
        }.start()
    }

    private fun runProbe(): String {
        val lines = mutableListOf<String>()
        fun ok(stage: String, detail: String = "") {
            lines += "PASS  $stage${if (detail.isBlank()) "" else " — $detail"}"
        }
        fun fail(stage: String, error: Throwable): String {
            val root = generateSequence(error) { it.cause }.last()
            val detail = "${root::class.java.name}: ${root.message ?: "no message"}"
            lines += "FAIL  $stage — $detail"
            return lines.joinToString("\n")
        }

        try {
            val clientClass = Class.forName(
                "com.github.auties00.cobalt.client.WhatsAppClient",
                true,
                classLoader
            )
            ok("Load WhatsAppClient")

            val builder = clientClass.getMethod("builder").invoke(null)
            ok("WhatsAppClient.builder()", builder.javaClass.name)

            val linked = builder.javaClass.getMethod("linkedApi").invoke(builder)
            ok("linkedApi()", linked.javaClass.name)

            val web = linked.javaClass.getMethod("webClient").invoke(linked)
            ok("webClient()", web.javaClass.name)

            try {
                val options = web.javaClass.getMethod("createConnection").invoke(web)
                ok("createConnection()", options.javaClass.name)
            } catch (error: Throwable) {
                return fail("createConnection()", error)
            }
        } catch (error: Throwable) {
            return fail("bootstrap", error)
        }

        lines += "READY  Cobalt linked-client bootstrap reached Android successfully."
        return lines.joinToString("\n")
    }
}
