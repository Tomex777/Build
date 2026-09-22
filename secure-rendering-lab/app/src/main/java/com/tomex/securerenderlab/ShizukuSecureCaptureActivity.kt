package com.tomex.securerenderlab

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import rikka.shizuku.Shizuku
import java.io.File

class ShizukuSecureCaptureActivity :
    BaseCaptureActivity(),
    Shizuku.OnRequestPermissionResultListener,
    ServiceConnection {

    companion object {
        private const val SHIZUKU_PERMISSION_REQUEST = 7201
    }

    private lateinit var shizukuStatus: TextView
    private lateinit var shizukuPreview: ImageView

    private var remote: ISecureCaptureService? = null
    private var binding = false
    private var lastSecureCapture: File? = null

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(packageName, ShizukuSecureCaptureService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("secure_capture")
            .debuggable(false)
            .version(1)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val (scroll, root) = screenScroll()
        root.addView(heading("A · Shizuku secure-layer capture"))
        root.addView(
            bodyText(
                "This screen is protected with FLAG_SECURE. The Shizuku test runs the capture code under Android's shell UID and asks WindowManager for captureSecureLayers=true. Root is not required when Shizuku is running through Wireless debugging/ADB."
            )
        )

        root.addCard(secretPanel("Secure test content", "A-CAPTURE 8472"))

        root.addCard(
            card().apply {
                addView(heading("Shizuku connection", 18f))
                shizukuStatus = statusText("Checking Shizuku…")
                addView(shizukuStatus)
                addView(
                    labButton("Connect / request Shizuku permission") {
                        connectOrRequestShizuku()
                    }
                )
            }
        )

        root.addCard(
            card().apply {
                addView(heading("Secure-layer capture", 18f))
                addView(
                    bodyText(
                        "First capture this screen. If A works, the image below should contain A-CAPTURE 8472 even though this Activity has FLAG_SECURE."
                    )
                )
                addView(
                    labButton("Capture this FLAG_SECURE screen") {
                        captureThroughShizuku("Immediate secure capture")
                    }
                )
                addView(
                    labButton("Capture in 5 seconds · switch apps") {
                        scheduleExternalCapture()
                    }
                )
                shizukuPreview = previewImage()
                addView(shizukuPreview)
            }
        )

        root.addCard(
            card().apply {
                addView(heading("Control test", 18f))
                addView(
                    bodyText(
                        "Use ordinary MediaProjection below while this Activity remains FLAG_SECURE. Android should omit or blank this secure Activity. That gives us a direct A/B comparison on the same phone."
                    )
                )
            }
        )
        root.addCard(capturePanel())

        root.addCard(
            card().apply {
                addView(heading("Scope", 18f))
                addView(
                    bodyText(
                        "This experiment requests secure layers only. It deliberately does not request allowProtected, so hardware-protected DRM buffers remain outside this test."
                    )
                )
            }
        )

        setContentView(scroll)

        Shizuku.addRequestPermissionResultListener(this)
        refreshShizukuState()
    }

    override fun onResume() {
        super.onResume()
        refreshShizukuState()
        if (hasShizukuPermission()) {
            bindUserServiceIfNeeded()
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(this)
        if (binding || remote != null) {
            runCatching {
                Shizuku.unbindUserService(userServiceArgs, this, true)
            }
        }
        remote = null
        binding = false
        super.onDestroy()
    }

    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        if (requestCode != SHIZUKU_PERMISSION_REQUEST) return

        runOnUiThread {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                shizukuStatus.text = "Shizuku permission granted. Binding shell service…"
                bindUserServiceIfNeeded()
            } else {
                shizukuStatus.text =
                    "Shizuku permission denied. Secure-layer capture cannot run."
            }
        }
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        remote = ISecureCaptureService.Stub.asInterface(service)
        binding = false

        val uid = runCatching { remote?.serviceUid }.getOrNull()
        shizukuStatus.text =
            "Shizuku user service connected"
                + if (uid == null) "" else " · UID " + uid
                + if (uid == 2000) " (shell)" else ""
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        remote = null
        binding = false
        runOnUiThread {
            shizukuStatus.text = "Shizuku user service disconnected."
        }
    }

    private fun refreshShizukuState() {
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!running) {
            shizukuStatus.text =
                "Shizuku is not running. On an unrooted phone, start Shizuku using Wireless debugging/ADB, then return here."
            return
        }

        if (hasShizukuPermission()) {
            if (remote == null) {
                shizukuStatus.text = "Shizuku is running and permission is granted."
            }
        } else {
            shizukuStatus.text =
                "Shizuku is running, but this app has not been granted permission yet."
        }
    }

    private fun connectOrRequestShizuku() {
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!running) {
            shizukuStatus.text =
                "Start Shizuku first. Root is optional; Wireless debugging/ADB mode is enough."
            return
        }

        if (hasShizukuPermission()) {
            bindUserServiceIfNeeded()
        } else {
            shizukuStatus.text = "Waiting for Shizuku permission…"
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST)
        }
    }

    private fun hasShizukuPermission(): Boolean =
        runCatching {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

    private fun bindUserServiceIfNeeded() {
        if (remote != null || binding) return
        if (!hasShizukuPermission()) return

        binding = true
        shizukuStatus.text = "Binding Shizuku shell service…"
        runCatching {
            Shizuku.bindUserService(userServiceArgs, this)
        }.onFailure {
            binding = false
            shizukuStatus.text =
                "Could not bind Shizuku service: " +
                    it.javaClass.simpleName + ": " + (it.message ?: "unknown error")
        }
    }

    private fun scheduleExternalCapture() {
        val service = remote
        if (service == null) {
            shizukuStatus.text = "Connect the Shizuku service first."
            return
        }

        shizukuStatus.text =
            "5-second capture armed. Switching this lab to the background now…"
        moveTaskToBack(true)

        Thread {
            Thread.sleep(5_000L)
            performSecureCapture(service, "Delayed secure capture")
        }.start()
    }

    private fun captureThroughShizuku(label: String) {
        val service = remote
        if (service == null) {
            shizukuStatus.text = "Connect the Shizuku service first."
            return
        }

        shizukuStatus.text = label + " in progress…"
        Thread {
            performSecureCapture(service, label)
        }.start()
    }

    private fun performSecureCapture(
        service: ISecureCaptureService,
        label: String
    ) {
        try {
            val descriptor = service.captureSecureDisplay()
            val remoteStatus = runCatching { service.lastStatus }.getOrDefault("No status")

            if (descriptor == null) {
                runOnUiThread {
                    shizukuStatus.text = label + " failed.\n" + remoteStatus
                }
                return
            }

            val file =
                File(cacheDir, "shizuku-secure-" + System.currentTimeMillis() + ".png")

            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap == null) {
                runOnUiThread {
                    shizukuStatus.text =
                        label + " produced a file that BitmapFactory could not decode.\n" +
                            remoteStatus
                }
                return
            }

            lastSecureCapture = file
            runOnUiThread {
                shizukuPreview.setImageBitmap(bitmap)
                shizukuStatus.text =
                    label + " complete · " + bitmap.width + "×" + bitmap.height +
                        "\n" + remoteStatus +
                        "\nCached as: " + file.name
            }
        } catch (t: Throwable) {
            runOnUiThread {
                shizukuStatus.text =
                    label + " failed: " +
                        t.javaClass.simpleName + ": " + (t.message ?: "unknown error")
            }
        }
    }
}
