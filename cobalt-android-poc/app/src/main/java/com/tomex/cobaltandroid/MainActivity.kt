package com.tomex.cobaltandroid

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import java.nio.file.Path

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
            Log.e(tag, "Probe failed at $stage", error)
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

            val factoryClass = Class.forName(
                "com.github.auties00.cobalt.store.linked.LinkedWhatsAppStoreFactory",
                true,
                classLoader
            )
            ok("Load LinkedWhatsAppStoreFactory")

            val webMethod = linked.javaClass.getMethod("webClient", factoryClass)

            try {
                val temporaryFactory = factoryClass.getMethod("temporary").invoke(null)
                val tempWeb = webMethod.invoke(linked, temporaryFactory)
                ok("webClient(temporary)", tempWeb.javaClass.name)
                val tempOptions = tempWeb.javaClass.getMethod("createConnection").invoke(tempWeb)
                ok("temporary createConnection()", tempOptions.javaClass.name)
            } catch (error: Throwable) {
                return fail("temporary createConnection()", error)
            }

            try {
                val storeDir: Path = filesDir.toPath().resolve("cobalt-poc")
                val persistentFactory = factoryClass
                    .getMethod("persistent", Path::class.java)
                    .invoke(null, storeDir)
                ok("persistent(filesDir)", storeDir.toString())

                val persistentWeb = webMethod.invoke(linked, persistentFactory)
                ok("webClient(persistent)", persistentWeb.javaClass.name)
                val persistentOptions = persistentWeb.javaClass
                    .getMethod("createConnection")
                    .invoke(persistentWeb)
                ok("persistent createConnection()", persistentOptions.javaClass.name)
            } catch (error: Throwable) {
                return fail("persistent createConnection()", error)
            }
        } catch (error: Throwable) {
            return fail("bootstrap", error)
        }

        lines += "READY  Cobalt linked-client stores initialize on Android successfully."
        return lines.joinToString("\n")
    }
}
