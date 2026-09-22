package com.tomex.securerenderlab

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

fun Context.dp(value: Int): Int =
    (value * resources.displayMetrics.density).roundToInt()

fun Context.screenScroll(): Pair<ScrollView, LinearLayout> {
    val scroll = ScrollView(this).apply {
        isFillViewport = true
        setBackgroundColor(Color.rgb(246, 247, 249))
    }
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(24), dp(20), dp(36))
    }
    scroll.addView(
        root,
        ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    )
    return scroll to root
}

fun Context.heading(text: String, sizeSp: Float = 28f): TextView =
    TextView(this).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(Color.rgb(24, 27, 32))
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, 0, 0, dp(8))
    }

fun Context.bodyText(text: String, sizeSp: Float = 15f): TextView =
    TextView(this).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(Color.rgb(76, 82, 92))
        setLineSpacing(0f, 1.15f)
        setPadding(0, 0, 0, dp(8))
    }

fun Context.statusText(text: String): TextView =
    TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(Color.rgb(40, 70, 120))
        setPadding(0, dp(6), 0, dp(8))
    }

fun Context.labButton(text: String, onClick: () -> Unit): Button =
    Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 16f
        minHeight = dp(52)
        setOnClickListener { onClick() }
    }

fun Context.card(): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        setBackgroundColor(Color.WHITE)
        elevation = dp(2).toFloat()
    }

fun LinearLayout.addCard(view: View) {
    addView(
        view,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = context.dp(14)
        }
    )
}

fun LinearLayout.addGap(dp: Int = 8) {
    addView(View(context), LinearLayout.LayoutParams(1, context.dp(dp)))
}

fun Context.previewImage(): ImageView =
    ImageView(this).apply {
        adjustViewBounds = true
        scaleType = ImageView.ScaleType.FIT_CENTER
        setBackgroundColor(Color.rgb(232, 234, 238))
        minimumHeight = dp(180)
        contentDescription = "Captured frame preview"
    }

fun Context.secretPanel(label: String, value: String): LinearLayout =
    card().apply {
        gravity = Gravity.CENTER
        addView(heading(label, 16f))
        addView(
            TextView(this@secretPanel).apply {
                text = value
                textSize = 30f
                setTextColor(Color.rgb(20, 20, 20))
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(18), dp(8), dp(18))
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }
