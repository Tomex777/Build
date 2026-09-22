package com.example.whatsapp.data.scripts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.Undefined

class NightScriptSandboxTest {
    private val factory = NightSandboxContextFactory()

    @Test
    fun safeScopeDoesNotExposeJavaPackageGlobals() {
        factory.runWithDeadline(500) { cx ->
            val scope = cx.initSafeStandardObjects()

            assertEquals(
                "undefined",
                Context.toString(
                    cx.evaluateString(
                        scope,
                        "typeof Packages",
                        "sandbox-test",
                        1,
                        null,
                    )
                ),
            )
            assertEquals(
                "undefined",
                Context.toString(
                    cx.evaluateString(
                        scope,
                        "typeof java",
                        "sandbox-test",
                        1,
                        null,
                    )
                ),
            )
        }
    }

    @Test
    fun javaClassAccessCannotEscapeThroughGetClass() {
        factory.runWithDeadline(500) { cx ->
            val scope = cx.initSafeStandardObjects()
            val result = cx.evaluateString(
                scope,
                """
                try {
                  ({}).getClass();
                  "escaped";
                } catch (error) {
                  "blocked";
                }
                """.trimIndent(),
                "sandbox-test",
                1,
                null,
            )
            assertEquals("blocked", Context.toString(result))
        }
    }

    @Test
    fun runawayScriptsHitInstructionDeadline() {
        val started = System.nanoTime()
        try {
            factory.runWithDeadline(75) { cx ->
                val scope = cx.initSafeStandardObjects()
                cx.evaluateString(
                    scope,
                    "while (true) {}",
                    "timeout-test",
                    1,
                    null,
                )
                Undefined.instance
            }
            fail("Expected the sandbox deadline to stop the script.")
        } catch (error: Throwable) {
            assertTrue(
                "Unexpected error: " + error,
                generateSequence(error) { it.cause }
                    .any { it.message.orEmpty().contains("timed out", ignoreCase = true) }
            )
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000L
        assertTrue("Script timeout took too long: " + elapsedMs + " ms", elapsedMs < 2_000L)
    }
}
