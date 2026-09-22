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
                "A real-device lab for comparing ordinary rendering, FLAG_SECURE, secure SurfaceView layers, screen capture, and DRM capabilities."
            )
        )

        root.addCard(deviceCard())
        root.addCard(
            labCard(
                "1 · FLAG_SECURE window",
                "Toggle the real WindowManager secure flag, then ask MediaProjection to capture one frame. The phone should still show the test content while the capture path omits the protected window.",
                "Open FLAG_SECURE lab"
            ) {
                startActivity(Intent(this, FlagSecureActivity::class.java))
            }
        )
        root.addCard(
            labCard(
                "2 · Secure SurfaceView",
                "Render two surfaces in one Activity: one ordinary and one created with SurfaceView.setSecure(true). Compare them in a captured frame.",
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
                    "It does not request privileged CAPTURE_SECURE_VIDEO_OUTPUT access, patch SurfaceFlinger, hook other apps, or bypass DRM. The point is to expose the normal Android protection boundaries so you can see exactly where captured pixels disappear."
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
