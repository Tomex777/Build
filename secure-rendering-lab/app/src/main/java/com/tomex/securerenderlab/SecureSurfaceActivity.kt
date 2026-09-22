package com.tomex.securerenderlab

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

class SecureSurfaceActivity : BaseCaptureActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // setSecure(true) must happen before the SurfaceView's containing window is attached.
        val normalSurface = PatternSurfaceView(this, secure = false)
        val secureSurface = PatternSurfaceView(this, secure = true)
        val replica = CaptureReplicaView(this).apply {
            visibility = View.GONE
        }

        val (scroll, root) = screenScroll()
        root.addView(heading("Secure SurfaceView"))
        root.addView(
            bodyText(
                "Both panels are independent SurfaceView layers in the same Activity. Only the second is marked secure before the window is attached."
            )
        )

        root.addCard(surfaceCard("Ordinary SurfaceView", normalSurface))
        root.addCard(surfaceCard("Secure SurfaceView", secureSurface))

        val leakCard = card().apply {
            addView(heading("Controlled source-copy leak", 18f))
            addView(
                bodyText(
                    "This does not disable SurfaceFlinger protection. Instead, the app intentionally draws the same secret into an ordinary View as well. If a screenshot sees the replica while the secure SurfaceView remains black, you are seeing an app-level leak before secure composition."
                )
            )

            val toggle = Switch(this@SecureSurfaceActivity).apply {
                text = "Expose secure test data through normal View"
                textSize = 16f
                minHeight = dp(52)
            }
            addView(toggle)

            addView(
                replica,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(150)
                )
            )

            val leakState =
                statusText("Replica is OFF · secret exists only in the secure SurfaceView layer.")
            addView(leakState)

            toggle.setOnCheckedChangeListener { _, checked ->
                replica.visibility = if (checked) View.VISIBLE else View.GONE
                leakState.text =
                    if (checked) {
                        "Replica is ON · the same source data is now deliberately exposed through an ordinary layer."
                    } else {
                        "Replica is OFF · secret exists only in the secure SurfaceView layer."
                    }
            }
        }
        root.addCard(leakCard)

        val probeStatus = statusText("No PixelCopy probe run yet.")
        val probeCard = card().apply {
            addView(heading("PixelCopy readback probe", 18f))
            addView(
                bodyText(
                    "PixelCopy asks Android to copy a rendered SurfaceView into app-readable bitmap memory. This is a probe, not a workaround. The result can differ by device/vendor implementation."
                )
            )
            addView(
                labButton("Probe both surfaces") {
                    runPixelCopyProbe(
                        normalSurface = normalSurface,
                        secureSurface = secureSurface,
                        status = probeStatus
                    )
                }
            )
            addView(probeStatus)
        }
        root.addCard(probeCard)

        val explainer = card().apply {
            addView(heading("What to look for", 18f))
            addView(
                bodyText(
                    "On the phone you should see both SurfaceViews. In an ordinary MediaProjection frame, the normal surface should remain visible while the secure surface is expected to be blank or black. If the controlled replica is enabled, that replica should remain capturable because it is deliberately non-secure."
                )
            )
        }
        root.addCard(explainer)
        root.addCard(capturePanel())

        setContentView(scroll)
    }

    private fun runPixelCopyProbe(
        normalSurface: SurfaceView,
        secureSurface: SurfaceView,
        status: TextView
    ) {
        val handler = Handler(Looper.getMainLooper())
        val width = normalSurface.width.coerceAtLeast(1)
        val height = normalSurface.height.coerceAtLeast(1)

        val normalBitmap =
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val secureBitmap =
            Bitmap.createBitmap(
                secureSurface.width.coerceAtLeast(1),
                secureSurface.height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )

        status.text = "Probing normal SurfaceView…"

        try {
            PixelCopy.request(
                normalSurface,
                normalBitmap,
                { normalResult ->
                    val normalText = pixelCopyName(normalResult)
                    status.text =
                        "Normal SurfaceView: " + normalText +
                            "\nProbing secure SurfaceView…"

                    try {
                        PixelCopy.request(
                            secureSurface,
                            secureBitmap,
                            { secureResult ->
                                status.text =
                                    "Normal SurfaceView: " + normalText +
                                        "\nSecure SurfaceView: " +
                                        pixelCopyName(secureResult) +
                                        "\n\nSUCCESS means Android returned a bitmap. It does not automatically mean the bitmap contains useful protected pixels."
                                normalBitmap.recycle()
                                secureBitmap.recycle()
                            },
                            handler
                        )
                    } catch (t: Throwable) {
                        status.text =
                            "Normal SurfaceView: " + normalText +
                                "\nSecure SurfaceView: threw " +
                                t.javaClass.simpleName +
                                " (" + (t.message ?: "no message") + ")"
                        normalBitmap.recycle()
                        secureBitmap.recycle()
                    }
                },
                handler
            )
        } catch (t: Throwable) {
            status.text =
                "Normal SurfaceView probe threw " +
                    t.javaClass.simpleName +
                    " (" + (t.message ?: "no message") + ")"
            normalBitmap.recycle()
            secureBitmap.recycle()
        }
    }

    private fun pixelCopyName(result: Int): String =
        when (result) {
            PixelCopy.SUCCESS -> "SUCCESS"
            PixelCopy.ERROR_DESTINATION_INVALID -> "ERROR_DESTINATION_INVALID"
            PixelCopy.ERROR_SOURCE_INVALID -> "ERROR_SOURCE_INVALID"
            PixelCopy.ERROR_SOURCE_NO_DATA -> "ERROR_SOURCE_NO_DATA"
            PixelCopy.ERROR_TIMEOUT -> "ERROR_TIMEOUT"
            else -> "result=" + result
        }

    private fun surfaceCard(
        title: String,
        surface: SurfaceView
    ): LinearLayout =
        card().apply {
            addView(heading(title, 18f))
            addView(
                surface,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(210)
                )
            )
        }
}

private class PatternSurfaceView(
    context: Context,
    private val secure: Boolean
) : SurfaceView(context), SurfaceHolder.Callback {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        setSecure(secure)
        holder.addCallback(this)

        bgPaint.color =
            if (secure) Color.rgb(70, 55, 115)
            else Color.rgb(35, 110, 85)

        stripePaint.color = Color.argb(90, 255, 255, 255)

        textPaint.color = Color.WHITE
        textPaint.textSize = context.dp(20).toFloat()
        textPaint.isFakeBoldText = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        drawPattern(holder)
    }

    override fun surfaceChanged(
        holder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int
    ) {
        drawPattern(holder)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) = Unit

    private fun drawPattern(holder: SurfaceHolder) {
        val canvas: Canvas = holder.lockCanvas() ?: return
        try {
            drawSurfacePattern(
                canvas = canvas,
                context = context,
                secure = secure
            )
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }
}

private class CaptureReplicaView(
    context: Context
) : View(context) {

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawSurfacePattern(
            canvas = canvas,
            context = context,
            secure = true,
            labelOverride = "EXPOSED COPY · 3141"
        )
    }
}

private fun drawSurfacePattern(
    canvas: Canvas,
    context: Context,
    secure: Boolean,
    labelOverride: String? = null
) {
    val bgPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color =
                if (secure) Color.rgb(70, 55, 115)
                else Color.rgb(35, 110, 85)
        }

    val stripePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 255, 255, 255)
        }

    val textPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = context.dp(20).toFloat()
            isFakeBoldText = true
        }

    canvas.drawColor(bgPaint.color)

    val step = context.dp(32)
    var x = -canvas.height
    while (x < canvas.width + canvas.height) {
        canvas.drawRect(
            x.toFloat(),
            0f,
            (x + context.dp(10)).toFloat(),
            canvas.height.toFloat(),
            stripePaint
        )
        x += step
    }

    val label =
        labelOverride
            ?: if (secure) "SECURE SURFACE · 3141"
            else "NORMAL SURFACE · 2718"

    canvas.drawText(
        label,
        context.dp(18).toFloat(),
        canvas.height / 2f,
        textPaint
    )
}
