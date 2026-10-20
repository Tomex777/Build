package reader.shared.android.navigation

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewPropertyAnimator
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import androidx.core.view.isVisible
import reader.shared.android.navigation.ViewerNavigation.NavigationRegion
import kotlin.math.abs

/**
 * Standalone-reader port of Mihon v0.19.9 ReaderNavigationOverlayView.
 * Keep Mihon's translucent region colors, central action labels, animation
 * duration and tap-to-dismiss mechanics; the string resource adapter uses
 * Yomi's five navigation enum actions instead of Mihon's app-wide i18n model.
 */
class ReaderNavigationOverlayView @JvmOverloads constructor(
    context: Context,
    attributes: AttributeSet? = null,
) : View(context, attributes) {
    private var currentAnimator: ViewPropertyAnimator? = null
    private var navigation: ViewerNavigation? = null
    private val regionPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        textSize = 64f
    }
    private val textBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.BLACK
        textSize = 64f
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    init {
        alpha = 0f
        isVisible = false
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "Tap zones. Tap to dismiss."
    }

    fun setNavigation(newNavigation: ViewerNavigation, showOnStart: Boolean = true) {
        val firstLaunch = navigation == null
        navigation = newNavigation
        invalidate()
        if (isVisible || (!showOnStart && firstLaunch) || newNavigation is DisabledNavigation) return

        currentAnimator?.cancel()
        currentAnimator = animate().alpha(1f).setDuration(FADE_DURATION).withStartAction {
            isVisible = true
        }.withEndAction {
            currentAnimator = null
        }
        currentAnimator?.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        navigation?.getRegions()?.forEach { region ->
            val rect = region.rectF
            canvas.withScale(width.toFloat(), height.toFloat()) {
                regionPaint.color = region.type.regionColor()
                drawRect(rect, regionPaint)
            }
            canvas.withTranslation(x = width * rect.left, y = height * rect.top) {
                val x = width * abs(rect.left - rect.right) / 2
                val y = height * abs(rect.top - rect.bottom) / 2
                drawText(region.type.actionName(), x, y, textBorderPaint)
                drawText(region.type.actionName(), x, y, textPaint)
            }
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (currentAnimator == null && isVisible) {
            currentAnimator = animate().alpha(0f).setDuration(FADE_DURATION).withEndAction {
                isVisible = false
                currentAnimator = null
            }
            currentAnimator?.start()
        }
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }
}

private fun NavigationRegion.actionName(): String = when (this) {
    NavigationRegion.MENU -> "Menu"
    NavigationRegion.PREV -> "Previous"
    NavigationRegion.NEXT -> "Next"
    NavigationRegion.LEFT -> "Left"
    NavigationRegion.RIGHT -> "Right"
}

private fun NavigationRegion.regionColor(): Int = when (this) {
    NavigationRegion.MENU -> Color.argb(0xCC, 0x95, 0x81, 0x8D)
    NavigationRegion.PREV -> Color.argb(0xCC, 0xFF, 0x77, 0x33)
    NavigationRegion.NEXT -> Color.argb(0xCC, 0x84, 0xE2, 0x96)
    NavigationRegion.LEFT -> Color.argb(0xCC, 0x7D, 0x11, 0x28)
    NavigationRegion.RIGHT -> Color.argb(0xCC, 0xA6, 0xCF, 0xD5)
}

private const val FADE_DURATION = 1000L
