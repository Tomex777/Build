package com.tomex.securerenderlab

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.util.concurrent.TimeUnit

class SystemCapabilityActivity : Activity() {

    private lateinit var privilegeStatus: TextView
    private lateinit var secureDisplayStatus: TextView
    private lateinit var rootStatus: TextView
    private lateinit var rootRequestStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val (scroll, root) = screenScroll()
        root.addView(heading("System capability detector"))
        root.addView(
            bodyText(
                "This screen checks what this APK can actually do on the current Android build. It does not patch SurfaceFlinger or silently request root."
            )
        )

        root.addCard(privilegeCard())
        root.addCard(secureDisplayProbeCard())
        root.addCard(rootEnvironmentCard())
        root.addCard(rootRequestCard())
        root.addCard(explainerCard())

        setContentView(scroll)

        refreshPrivilegeStatus()
        refreshRootIndicators()
    }

    private fun privilegeCard(): LinearLayout =
        card().apply {
            addView(heading("App privilege level", 18f))
            privilegeStatus = statusText("Checking…")
            addView(privilegeStatus)
            addView(
                labButton("Refresh privilege checks") {
                    refreshPrivilegeStatus()
                }
            )
        }

    private fun secureDisplayProbeCard(): LinearLayout =
        card().apply {
            addView(heading("Secure virtual display probe", 18f))
            addView(
                bodyText(
                    "Android reserves VIRTUAL_DISPLAY_FLAG_SECURE for callers holding CAPTURE_SECURE_VIDEO_OUTPUT. This probe first creates a normal private virtual display, then separately requests a secure one and reports the platform result."
                )
            )
            secureDisplayStatus =
                statusText("Not run yet.")
            addView(secureDisplayStatus)
            addView(
                labButton("Run secure-display probe") {
                    runSecureDisplayProbe()
                }
            )
        }

    private fun rootEnvironmentCard(): LinearLayout =
        card().apply {
            addView(heading("Root / modified-system indicators", 18f))
            addView(
                bodyText(
                    "These are indicators, not proof. Custom ROMs, engineering builds, or hidden root setups can make individual checks misleading."
                )
            )
            rootStatus = statusText("Checking…")
            addView(rootStatus)
            addView(
                labButton("Refresh root indicators") {
                    refreshRootIndicators()
                }
            )
        }

    private fun rootRequestCard(): LinearLayout =
        card().apply {
            addView(heading("Optional root-access test", 18f))
            addView(
                bodyText(
                    "This is the only test here that actually invokes su. It runs only when you tap the button and may show a Magisk/Superuser approval prompt."
                )
            )
            rootRequestStatus =
                statusText("Not requested.")
            addView(rootRequestStatus)
            addView(
                labButton("Request root once") {
                    requestRootOnce()
                }
            )
        }

    private fun explainerCard(): LinearLayout =
        card().apply {
            addView(heading("How to read the result", 18f))
            addView(
                bodyText(
                    "Normal APK + CAPTURE_SECURE_VIDEO_OUTPUT denied + secure virtual display rejected = the public app sandbox is behaving normally.\n\n" +
                        "Platform signature/system-app status or a successful secure-display request would indicate privileges beyond an ordinary APK.\n\n" +
                        "Root access is separate again: root can enable system modification, but the presence of su by itself does not automatically give this APK SurfaceFlinger capture privileges."
                )
            )
        }

    private fun refreshPrivilegeStatus() {
        val appInfo = applicationInfo
        val isSystem =
            appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
        val isUpdatedSystem =
            appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        val platformSigned =
            packageManager.checkSignatures(packageName, "android") ==
                PackageManager.SIGNATURE_MATCH

        val secureCapture =
            permissionState("android.permission.CAPTURE_SECURE_VIDEO_OUTPUT")
        val videoCapture =
            permissionState("android.permission.CAPTURE_VIDEO_OUTPUT")
        val framebuffer =
            permissionState("android.permission.READ_FRAME_BUFFER")

        privilegeStatus.text =
            "UID: " + Process.myUid() +
                "\nSystem app: " + yesNo(isSystem) +
                "\nUpdated system app: " + yesNo(isUpdatedSystem) +
                "\nPlatform signature match: " + yesNo(platformSigned) +
                "\n\nCAPTURE_SECURE_VIDEO_OUTPUT: " + secureCapture +
                "\nCAPTURE_VIDEO_OUTPUT: " + videoCapture +
                "\nREAD_FRAME_BUFFER: " + framebuffer
    }

    private fun permissionState(permission: String): String =
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            "GRANTED"
        } else {
            "DENIED"
        }

    private fun runSecureDisplayProbe() {
        val manager =
            getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

        val density = resources.displayMetrics.densityDpi.coerceAtLeast(1)

        val normalReader =
            ImageReader.newInstance(
                64,
                64,
                PixelFormat.RGBA_8888,
                2
            )

        val secureReader =
            ImageReader.newInstance(
                64,
                64,
                PixelFormat.RGBA_8888,
                2
            )

        var normalResult = "not attempted"
        var secureResult = "not attempted"

        try {
            val normalDisplay =
                manager.createVirtualDisplay(
                    "SecureLabNormalProbe",
                    64,
                    64,
                    density,
                    normalReader.surface,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                )

            normalResult =
                if (normalDisplay == null) {
                    "returned null"
                } else {
                    val flags = normalDisplay.display.flags
                    normalDisplay.release()
                    "SUCCESS (display flags=0x" + flags.toString(16) + ")"
                }
        } catch (t: Throwable) {
            normalResult =
                t.javaClass.simpleName + ": " + (t.message ?: "no message")
        }

        try {
            val secureDisplay =
                manager.createVirtualDisplay(
                    "SecureLabSecureProbe",
                    64,
                    64,
                    density,
                    secureReader.surface,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE
                )

            secureResult =
                if (secureDisplay == null) {
                    "returned null"
                } else {
                    val flags = secureDisplay.display.flags
                    secureDisplay.release()
                    "SUCCESS (display flags=0x" + flags.toString(16) + ")"
                }
        } catch (t: Throwable) {
            secureResult =
                t.javaClass.simpleName + ": " + (t.message ?: "no message")
        } finally {
            normalReader.close()
            secureReader.close()
        }

        secureDisplayStatus.text =
            "Normal private virtual display: " + normalResult +
                "\n\nSecure virtual display: " + secureResult +
                "\n\nPermission check: " +
                permissionState("android.permission.CAPTURE_SECURE_VIDEO_OUTPUT")
    }

    private fun refreshRootIndicators() {
        val suPaths =
            listOf(
                "/system/bin/su",
                "/system/xbin/su",
                "/sbin/su",
                "/vendor/bin/su",
                "/product/bin/su",
                "/data/local/bin/su",
                "/data/local/xbin/su"
            )

        val visibleSu =
            suPaths.filter { path ->
                runCatching {
                    val file = File(path)
                    file.exists() && file.canExecute()
                }.getOrDefault(false)
            }

        val whichSu =
            shellRead("command -v su")
                .ifBlank { "not found in PATH" }

        val verifiedBoot =
            getProp("ro.boot.verifiedbootstate")
                .ifBlank { "unavailable" }
        val vbmetaState =
            getProp("ro.boot.vbmeta.device_state")
                .ifBlank { "unavailable" }
        val debuggable =
            getProp("ro.debuggable")
                .ifBlank { "unavailable" }
        val roSecure =
            getProp("ro.secure")
                .ifBlank { "unavailable" }

        val selinux =
            runCatching {
                when (File("/sys/fs/selinux/enforce").readText().trim()) {
                    "1" -> "enforcing"
                    "0" -> "permissive"
                    else -> "unknown"
                }
            }.getOrDefault("unreadable")

        val testKeys =
            Build.TAGS?.contains("test-keys", ignoreCase = true) == true

        rootStatus.text =
            "Build tags: " + (Build.TAGS ?: "unknown") +
                "\nContains test-keys: " + yesNo(testKeys) +
                "\nVerified boot state: " + verifiedBoot +
                "\nVBMeta device state: " + vbmetaState +
                "\nro.debuggable: " + debuggable +
                "\nro.secure: " + roSecure +
                "\nSELinux: " + selinux +
                "\n\nwhich su: " + whichSu +
                "\nKnown executable su paths: " +
                if (visibleSu.isEmpty()) "none visible"
                else visibleSu.joinToString()
    }

    private fun requestRootOnce() {
        rootRequestStatus.text = "Invoking su -c id…"

        Thread {
            val result =
                try {
                    val process =
                        ProcessBuilder("su", "-c", "id")
                            .redirectErrorStream(true)
                            .start()

                    val completed =
                        process.waitFor(8, TimeUnit.SECONDS)

                    if (!completed) {
                        process.destroy()
                        "Timed out waiting for su."
                    } else {
                        val output =
                            process.inputStream
                                .bufferedReader()
                                .readText()
                                .trim()

                        "Exit " + process.exitValue() +
                            if (output.isBlank()) ""
                            else "\n" + output
                    }
                } catch (t: Throwable) {
                    t.javaClass.simpleName + ": " + (t.message ?: "no message")
                }

            runOnUiThread {
                rootRequestStatus.text = result
            }
        }.start()
    }

    private fun getProp(name: String): String =
        runCatching {
            val process =
                ProcessBuilder("getprop", name)
                    .redirectErrorStream(true)
                    .start()

            process.waitFor(2, TimeUnit.SECONDS)
            process.inputStream
                .bufferedReader()
                .readText()
                .trim()
        }.getOrDefault("")

    private fun shellRead(command: String): String =
        runCatching {
            val process =
                ProcessBuilder("sh", "-c", command)
                    .redirectErrorStream(true)
                    .start()

            process.waitFor(2, TimeUnit.SECONDS)
            process.inputStream
                .bufferedReader()
                .readText()
                .trim()
        }.getOrDefault("")

    private fun yesNo(value: Boolean): String =
        if (value) "yes" else "no"
}
