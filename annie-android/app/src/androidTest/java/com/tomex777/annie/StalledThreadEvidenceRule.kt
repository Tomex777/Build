package com.tomex777.annie

import android.util.Log
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Capture the blocked operation, including ActivityScenario teardown, before CI terminates it. */
internal class StalledThreadEvidenceRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val observer = Thread({
                try {
                    repeat(3) {
                        Thread.sleep(45_000)
                        Log.e("AnnieCanvasTest", "Stalled test evidence: ${description.methodName}")
                        Thread.getAllStackTraces().forEach { (thread, trace) ->
                            if (thread.name == "main" || thread.name.contains("Instr", true) ||
                                thread.name.contains("Test", true) || thread.name.contains("Binder", true)) {
                                Log.e("AnnieCanvasTest", "THREAD ${thread.name} ${thread.state}")
                                trace.forEach { Log.e("AnnieCanvasTest", "  at $it") }
                            }
                        }
                    }
                } catch (_: InterruptedException) {
                    // Completed tests cancel the observer, including successful teardown.
                }
            }, "AnnieTestEvidence").apply { isDaemon = true }
            observer.start()
            try { base.evaluate() } finally { observer.interrupt() }
        }
    }
}
