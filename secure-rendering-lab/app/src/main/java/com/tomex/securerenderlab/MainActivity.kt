package com.tomex.securerenderlab

import android.app.Activity
import android.content.Intent
import android.media.MediaDrm
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.widget.LinearLayout
import androidx.media3.common.C

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val (scroll, root) = screenScroll()
        root.addView(heading("Secure Rendering Lab"))
        root.addView(
            bodyText(
                "A real-device lab for comparing ordinary rendering, FLAG_SECURE, secure SurfaceView layers, controlled self-exposure experiments, screen capture, and DRM capabilities."
            )
        )

        root.addCard(deviceCard())
        root.addCard(
            labCard(
                "1 · FLAG_SECURE window",
                "Toggle the real WindowManager secure flag, compare MediaProjection output, then run a controlled self-bypass where the app removes its own flag for one capture.",
                "Open FLAG_SECURE lab"
            ) {
                startActivity(Intent(this, FlagSecureActivity::class.java))
            }
        )
        root.addCard(
            labCard(
                "2 · Secure SurfaceView",
                "Compare ordinary and secure SurfaceViews, intentionally leak the same source data through a normal View, and probe both surfaces with PixelCopy.",
                "Open Surface lab"
            ) {
                startActivity(Intent(this, SecureSurfaceActivity::class.java))
            }
        )
        root.addCard(
            labCard(
                "3 · DRM + protected-path inspector",
                "Inspect Widevine/ClearKey support and play a public ClearKey DASH test vector. This demonstrates real MediaDrm negotiation without pretending ClearKey is equivalent to hardware-secure Widevine.",
                "Open DRM lab"
            ) {
                startActivity(Intent(this, DrmLabActivity::class.java))
            }
        )

        val note = card().apply {
            addView(heading("What this app does not do", 18f))
            addView(
                bodyText(
                    "It does not request privileged CAPTURE_SECURE_VIDEO_OUTPUT access, patch SurfaceFlinger, hook other apps, or bypass another app's DRM/security. The controlled bypasses only make this lab expose its own test content so you can see exactly where the protection boundary sits."
                )
            )
        }
        root.addCard(note)

        setContentView(scroll)
    }

    private fun deviceCard(): LinearLayout {
        val display = if (Build.VERSION.SDK_INT >= 30) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }

        val displayFlags = display?.flags ?: 0
        val secureOutput = displayFlags and Display.FLAG_SECURE != 0
        val protectedBuffers =
            displayFlags and Display.FLAG_SUPPORTS_PROTECTED_BUFFERS != 0

        val widevine = MediaDrm.isCryptoSchemeSupported(C.WIDEVINE_UUID)
        val clearKey = MediaDrm.isCryptoSchemeSupported(C.CLEARKEY_UUID)

        return card().apply {
            addView(heading("This device", 18f))
            addView(
                bodyText(
                    "Android " + Build.VERSION.RELEASE +
                        " · API " + Build.VERSION.SDK_INT +
                        "\nModel: " + Build.MANUFACTURER + " " + Build.MODEL +
                        "\nDisplay secure output: " + yesNo(secureOutput) +
                        "\nDisplay protected buffers: " + yesNo(protectedBuffers) +
                        "\nWidevine MediaDrm: " + yesNo(widevine) +
                        "\nClearKey MediaDrm: " + yesNo(clearKey)
                )
            )
        }
    }

    private fun labCard(
        title: String,
        description: String,
        buttonText: String,
        onClick: () -> Unit
    ): LinearLayout =
        card().apply {
            addView(heading(title, 18f))
            addView(bodyText(description))
            addView(labButton(buttonText, onClick))
        }

    private fun yesNo(value: Boolean): String = if (value) "yes" else "no"
}
