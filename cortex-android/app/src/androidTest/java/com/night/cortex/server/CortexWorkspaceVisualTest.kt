package com.night.cortex.server

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.night.cortex.hosting.HostingFileEntry
import com.night.cortex.hosting.HostingSnapshot
import com.night.cortex.ui.theme.CortexTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class CortexWorkspaceVisualTest {
    @get:Rule
    val composeRule = createComposeRule()

    private enum class Page {
        CONSOLE,
        FILES,
        EDITOR,
        BACKUPS,
        STARTUP,
        SETTINGS,
        ACTIVITY,
    }

    @Test
    fun productionWorkspaceScreensRender() {
        val page = mutableStateOf(Page.CONSOLE)
        val base = ServerPanelState(
            configured = true,
            agentReachable = true,
            lastSuccessfulSyncAt = System.currentTimeMillis(),
            baseUrl = "https://cortex.example",
            hasToken = true,
            snapshot = HostingSnapshot(
                state = "active",
                cpuPercent = 17.0,
                memoryUsedBytes = 196L * 1024 * 1024,
                memoryLimitBytes = 512L * 1024 * 1024,
                diskUsedBytes = 6L * 1024 * 1024 * 1024,
                diskLimitBytes = 20L * 1024 * 1024 * 1024,
                uptimeMs = 7_200_000L,
            ),
            logs = listOf(
                "Night started successfully",
                "WhatsApp account Main connected",
                "Listening for messages",
                "WARN reconnect scheduled for Archive",
            ),
        )
        val filesState = base.copy(
            currentPath = "/",
            files = listOf(
                HostingFileEntry(name = "commands", type = "directory"),
                HostingFileEntry(name = "modules", type = "directory"),
                HostingFileEntry(name = "index.js", type = "file", sizeBytes = 4_216),
                HostingFileEntry(name = "package.json", type = "file", sizeBytes = 1_024),
            ),
        )
        val backupState = base.copy(
            backups = listOf(
                BackupEntry("project-2026-09-30.zip", 3_145_728, "2026-09-30T06:20:00Z", false),
                BackupEntry("private-2026-09-30.zip", 1_048_576, "2026-09-30T06:10:00Z", true),
            ),
        )
        val startupState = base.copy(
            startup = StartupInfo(
                runtime = "Node.js",
                version = "24.6.0",
                entryFile = "index.js",
                startCommand = "node index.js",
                startupMode = "enabled",
                additionalNodePackages = listOf("sharp", "ffmpeg-static"),
            ),
        )
        val settingsState = base.copy(
            commandSettings = listOf(
                CommandSetting(
                    key = "autoCc",
                    label = "Auto CC",
                    description = "Forward recovered messages automatically",
                    command = "autocc",
                    enabled = true,
                ),
            ),
            runtimeRegistry = RuntimeRegistry(
                version = 1,
                generatedAt = "2026-09-30T06:20:00Z",
                source = "live",
                modules = listOf(
                    RuntimeModule(
                        id = "core",
                        displayName = "Night Core",
                        version = "2.0.0",
                        status = "loaded",
                        enabled = true,
                        commands = listOf("status", "help"),
                        configuration = listOf(
                            RuntimeConfigField(
                                key = "mode",
                                label = "Recovery mode",
                                type = "select",
                                description = "Controls recovery behavior",
                            ),
                        ),
                        loadError = "",
                        lastReload = "2026-09-30T06:15:00Z",
                        moduleDirectory = "modules/core",
                        dependencies = listOf("baileys"),
                        permissions = listOf("whatsapp.send"),
                    ),
                ),
                commands = listOf(
                    RuntimeCommand(
                        name = "status",
                        moduleId = "core",
                        description = "Show Night status",
                        aliases = listOf("health"),
                        enabled = true,
                        permission = "",
                        usage = ".status",
                        error = "",
                    ),
                ),
            ),
        )
        val activityState = base.copy(
            activity = listOf(
                ActivityEntry(
                    id = "1",
                    at = "2026-09-30T06:22:00Z",
                    action = "cc.forwarded",
                    detail = "Recovered message forwarded to Main",
                ),
                ActivityEntry(
                    id = "2",
                    at = "2026-09-30T06:18:00Z",
                    action = "server:backup.create",
                    detail = "Project backup created",
                ),
                ActivityEntry(
                    id = "3",
                    at = "2026-09-30T06:15:00Z",
                    action = "mscc:commands.reload",
                    detail = "",
                ),
            ),
        )

        composeRule.setContent {
            CortexTheme {
                Box(Modifier.fillMaxSize().testTag("workspace-visual-root")) {
                    when (page.value) {
                        Page.CONSOLE -> ConsolePage(base, power = {}, refresh = {}, clear = {})
                        Page.FILES -> FilesPage(
                            state = filesState,
                            onUp = {},
                            onPath = {},
                            onRefresh = {},
                            onOpen = {},
                            onMore = {},
                            onNewFile = {},
                            onNewDirectory = {},
                            onUpload = {},
                        )
                        Page.EDITOR -> EditorScreen(
                            path = "/commands/recover.js",
                            content = "export default async function recover(message) {\n  return message\n}\n",
                            dirty = true,
                            busy = false,
                            onBack = {},
                            onChange = {},
                            onSave = {},
                        )
                        Page.BACKUPS -> BackupsPage(
                            state = backupState,
                            onRefresh = {},
                            onDownloadProject = {},
                            onCreate = {},
                            onDownload = {},
                            onDelete = {},
                            onRestore = {},
                        )
                        Page.STARTUP -> StartupPage(
                            state = startupState,
                            installDependencies = {},
                            power = {},
                            setStartupEnabled = {},
                        )
                        Page.SETTINGS -> SettingsPage(
                            state = settingsState,
                            onConnection = {},
                            onToggle = { _, _ -> },
                            onRefresh = {},
                            onNewCommand = {},
                            onReloadCommands = {},
                            onReloadModule = {},
                        )
                        Page.ACTIVITY -> ActivityPage(activityState, refresh = {})
                    }
                }
            }
        }

        capture("cortex-console-connected-emulator.png")
        show(page, Page.FILES, "cortex-files-emulator.png")
        show(page, Page.EDITOR, "cortex-editor-emulator.png")
        show(page, Page.BACKUPS, "cortex-backups-emulator.png")
        show(page, Page.STARTUP, "cortex-startup-emulator.png")
        show(page, Page.SETTINGS, "cortex-settings-emulator.png")
        show(page, Page.ACTIVITY, "cortex-activity-emulator.png")
    }

    private fun show(page: androidx.compose.runtime.MutableState<Page>, next: Page, name: String) {
        composeRule.runOnUiThread { page.value = next }
        composeRule.waitForIdle()
        capture(name)
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val node = composeRule.onNodeWithTag("workspace-visual-root", useUnmergedTree = true)
            .assertIsDisplayed()
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        // Capture the actual Compose surface instead of the headless emulator
        // framebuffer. Android 16 ATD/SwiftShader can return an all-black host
        // color buffer even while this exact node is visible and interactive.
        val bitmap = node.captureToImage().asAndroidBitmap()
        val file = File(
            instrumentation.targetContext.cacheDir,
            name,
        )
        FileOutputStream(file).use { stream ->
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)) {
                "Unable to encode Cortex workspace visual evidence"
            }
        }
        check(file.length() > 0L) { "Cortex workspace visual evidence is empty" }
    }
}
