package com.tomex.securerenderlab

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.LinearLayout

class SecureSurfaceActivity : BaseCaptureActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // setSecure(true) must happen before the SurfaceView's containing window is attached.
        val normalSurface = PatternSurfaceView(this, secure = false)
        val secureSurface = PatternSurfaceView(this, secure = true)

        val (scroll, root) = screenScroll()
        root.addView(heading("Secure SurfaceView"))
        root.addView(
            bodyText(
                "Both panels are independent SurfaceView layers in the same Activity. Only the second is marked secure before the window is attached."
            )
        )

        root.addCard(surfaceCard("Ordinary SurfaceView", normalSurface))
        root.addCard(surfaceCard("Secure SurfaceView", secureSurface))

        val explainer = card().apply {
            addView(heading("What to look for", 18f))
            addView(
                bodyText(
                    "On the phone you should see both surfaces. In an ordinary MediaProjection frame, the normal surface should remain visible while the secure surface is expected to be blank or black."
                )
            )
        }
        root.addCard(explainer)
        root.addCard(capturePanel())

        setContentView(scroll)
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
                if (secure) "SECURE SURFACE · 3141"
                else "NORMAL SURFACE · 2718"

            canvas.drawText(
                label,
                context.dp(18).toFloat(),
                (canvas.height / 2f),
                textPaint
            )
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }
}
