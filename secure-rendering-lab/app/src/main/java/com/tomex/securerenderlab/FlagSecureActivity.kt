package com.tomex.securerenderlab

import android.os.Bundle
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Switch

class FlagSecureActivity : BaseCaptureActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val (scroll, root) = screenScroll()
        root.addView(heading("FLAG_SECURE window"))
        root.addView(
            bodyText(
                "This screen toggles WindowManager.LayoutParams.FLAG_SECURE on the real Activity window. Compare what you see on the phone with the MediaProjection observer frame below."
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

            val state = statusText("FLAG_SECURE is OFF")
            addView(state)

            toggle.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    state.text =
                        "FLAG_SECURE is ON · ordinary screenshots/recording should omit this window."
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    state.text =
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

        val explainer = card().apply {
            addView(heading("Expected result", 18f))
            addView(
                bodyText(
                    "With the flag OFF, the observer image should contain this Activity. With it ON, Android's insecure capture output should omit or black the protected window even though it remains visible on the physical display."
                )
            )
        }
        root.addCard(explainer)
        root.addCard(capturePanel())

        setContentView(scroll)
    }
}
