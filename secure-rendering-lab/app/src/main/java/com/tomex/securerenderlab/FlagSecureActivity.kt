package com.tomex.securerenderlab

import android.os.Bundle
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Switch

class FlagSecureActivity : BaseCaptureActivity() {

    private var protectionEnabled = false
    private var restoreProtectionAfterCapture = false
    private lateinit var stateText: android.widget.TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val (scroll, root) = screenScroll()
        root.addView(heading("FLAG_SECURE window"))
        root.addView(
            bodyText(
                "This screen toggles WindowManager.LayoutParams.FLAG_SECURE on the real Activity window. The controlled bypass below does not defeat Android: this app deliberately removes its own secure flag before capture, then restores it."
            )
        )

        val control = card().apply {
            addView(heading("Window protection", 18f))
            val toggle = Switch(this@FlagSecureActivity).apply {
                text = "Protect this Activity with FLAG_SECURE"
                textSize = 16f
                minHeight = dp(52)
                isChecked = false
            }
            addView(toggle)

            stateText = statusText("FLAG_SECURE is OFF")
            addView(stateText)

            toggle.setOnCheckedChangeListener { _, checked ->
                protectionEnabled = checked
                applyProtection(checked)
                stateText.text =
                    if (checked) {
                        "FLAG_SECURE is ON · ordinary screenshots/recording should omit this window."
                    } else {
                        "FLAG_SECURE is OFF · ordinary screenshots/recording can include this window."
                    }
            }
        }
        root.addCard(control)

        root.addCard(
            secretPanel(
                "Visible test content",
                "SECRET 8472"
            )
        )

        val bypass = card().apply {
            addView(heading("Controlled self-bypass", 18f))
            addView(
                bodyText(
                    "Keep FLAG_SECURE on, then tap below. The app temporarily clears its own secure flag, requests one ordinary MediaProjection frame, and restores the flag when capture finishes. This shows that FLAG_SECURE is a compositor policy, not encryption of the app's pixels."
                )
            )
            addView(
                labButton("Temporarily expose + capture") {
                    if (!protectionEnabled) {
                        stateText.text =
                            "Turn FLAG_SECURE on first so the difference is visible."
                        return@labButton
                    }

                    restoreProtectionAfterCapture = true
                    applyProtection(false)
                    stateText.text =
                        "CONTROLLED BYPASS ACTIVE · this app removed FLAG_SECURE for the capture."
                    requestOneFrame()
                }
            )
        }
        root.addCard(bypass)

        val explainer = card().apply {
            addView(heading("Compare three states", 18f))
            addView(
                bodyText(
                    "1) FLAG_SECURE off: capture should see the Activity.\n" +
                        "2) FLAG_SECURE on: capture should omit it.\n" +
                        "3) Controlled self-bypass: capture sees it only because the Activity itself intentionally removed protection."
                )
            )
        }
        root.addCard(explainer)
        root.addCard(capturePanel())

        setContentView(scroll)
    }

    override fun onObserverCaptureFinished(path: String?, error: String?) {
        if (!restoreProtectionAfterCapture) return

        restoreProtectionAfterCapture = false
        if (protectionEnabled) {
            applyProtection(true)
            stateText.text =
                if (error == null) {
                    "Capture complete · FLAG_SECURE restored."
                } else {
                    "Capture ended with an error · FLAG_SECURE restored."
                }
        }
    }

    override fun onStop() {
        if (restoreProtectionAfterCapture && protectionEnabled) {
            restoreProtectionAfterCapture = false
            applyProtection(true)
        }
        super.onStop()
    }

    private fun applyProtection(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
