package com.night.cortex.server

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.night.cortex.hosting.HostingSnapshot
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineDashboardCacheTest {
    @Test
    fun restoresReadOnlyAccountsButNeverStoresSecrets() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.cacheDir, "offline-dashboard-test").apply { mkdirs() }
        val isolatedContext: Context = object : ContextWrapper(target) {
            override fun getNoBackupFilesDir(): File = directory
        }
        val cache = OfflineDashboardCache(isolatedContext)
        cache.clear()
        try {
            val state = ServerPanelState(
                configured = true,
                agentReachable = true,
                snapshot = HostingSnapshot("active", cpuPercent = 12.5, uptimeMs = 9_000L),
                pairing = PairingState(
                    version = "2.3",
                    destination = "A",
                    accounts = listOf(
                        PairingAccount(
                            id = "A", enabled = true, connected = true, status = "connected",
                            numberMasked = "234•••0531", indexCount = 4, indexLimit = 5000,
                            pairingMode = "qr", pairingCode = "SENSITIVE-PAIR-CODE",
                            pairingQr = "SENSITIVE-QR-IMAGE", pairingError = "",
                            displayName = "main", role = "owner",
                        )
                    ),
                ),
                logs = listOf("SENSITIVE-CONSOLE-LOG"),
            )
            cache.save("https://one.example", "SECRET-TOKEN", state)
            val restored = cache.load("https://one.example", "SECRET-TOKEN")
            assertNotNull(restored)
            assertEquals("active", restored!!.snapshot!!.state)
            assertEquals("main", restored.pairing!!.accounts.single().displayName)
            assertTrue(restored.pairing!!.accounts.single().pairingQr.isEmpty())
            assertTrue(restored.pairing!!.accounts.single().pairingCode.isEmpty())
            assertNull(cache.load("https://two.example", "SECRET-TOKEN"))
            assertNull(cache.load("https://one.example", "OTHER-TOKEN"))
            val bytes = File(directory, "cortex-dashboard-v1.json").readText()
            assertFalse(bytes.contains("SECRET-TOKEN"))
            assertFalse(bytes.contains("SENSITIVE-QR-IMAGE"))
            assertFalse(bytes.contains("SENSITIVE-PAIR-CODE"))
            assertFalse(bytes.contains("SENSITIVE-CONSOLE-LOG"))
        } finally {
            cache.clear()
            directory.deleteRecursively()
        }
    }
}
