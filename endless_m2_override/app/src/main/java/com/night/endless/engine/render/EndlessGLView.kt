package com.night.endless.engine.render

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import kotlin.math.pow

class EndlessGLView(
    context: Context,
    onSelectionChanged: (String?) -> Unit
) : GLSurfaceView(context) {
    val endlessRenderer = EndlessRenderer(context, onSelectionChanged)
    private var lastX = 0f
    private var lastY = 0f
    private var movePointerId = MotionEvent.INVALID_POINTER_ID
    private var lookPointerId = MotionEvent.INVALID_POINTER_ID
    private var lastLookX = 0f
    private var lastLookY = 0f
    private var frameTickerActive = false
    private var sceneOccluded = false
    private var lastFrameRequestNanos = 0L

    /**
     * When the full-screen galactic/cosmic view covers the 3D solar system,
     * preserve a low-rate orbital clock without burning a full GLES render
     * every display refresh behind opaque Compose content.
     */
    fun setSceneOccluded(occluded: Boolean) {
        if (sceneOccluded == occluded) return
        sceneOccluded = occluded
        lastFrameRequestNanos = 0L // First visible frame resumes immediately.
        if (occluded) cancelSurfaceControls()
    }

    // Drive GLES from display vsync, not a busy GL-thread spin loop.
    // This keeps the rendering thread asleep between display opportunities,
    // leaving CPU time for the Compose HUD and two-finger touch processing.
    // requestRender() coalesces on slow GPU frames and works at 60/90/120Hz.
    private val frameTicker = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!frameTickerActive || !isAttachedToWindow) return
            if (RendererFramePacing.shouldRequest(
                    sceneOccluded, lastFrameRequestNanos, frameTimeNanos
                )) {
                lastFrameRequestNanos = frameTimeNanos
                requestRender()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun startFrameTicker() {
        if (frameTickerActive || !isAttachedToWindow) return
        frameTickerActive = true
        requestRender()
        Choreographer.getInstance().postFrameCallback(frameTicker)
    }

    private fun stopFrameTicker() {
        frameTickerActive = false
        lastFrameRequestNanos = 0L
        Choreographer.getInstance().removeFrameCallback(frameTicker)
    }

    /** True physical thumb position for a passive, non-touch-consuming Compose HUD. */
    var onSurfaceStickChanged: ((Float, Float) -> Unit)? = null

    private fun resetSurfaceMovement() {
        movePointerId = MotionEvent.INVALID_POINTER_ID
        endlessRenderer.setSurfaceMovementInput(0f, 0f)
        onSurfaceStickChanged?.invoke(0f, 0f)
    }

    fun cancelSurfaceControls() {
        // A lifecycle cancellation is not a finger-up event; don't advance
        // held movement through a paused/detached app.
        movePointerId = MotionEvent.INVALID_POINTER_ID
        endlessRenderer.cancelSurfaceMovementInput()
        onSurfaceStickChanged?.invoke(0f, 0f)
        lookPointerId = MotionEvent.INVALID_POINTER_ID
    }

    private fun beginSurfacePointer(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)
        val x = event.getX(index)
        val y = event.getY(index)
        val d = resources.displayMetrics.density
        if (movePointerId == MotionEvent.INVALID_POINTER_ID &&
            SurfaceJoystickMath.isInMoveZone(x, y, height, d)) {
            movePointerId = id
            updateStick(x, y)
        } else if (lookPointerId == MotionEvent.INVALID_POINTER_ID &&
            SurfaceJoystickMath.isInLookZone(x, width)) {
            // Only the right half aims. An accidental left-side tap should
            // never steer the camera or steal the movement pointer.
            lookPointerId = id
            lastLookX = x
            lastLookY = y
        }
    }

    private fun updateStick(x: Float, y: Float) {
        val d = resources.displayMetrics.density
        val dx = x - SurfaceJoystickMath.centerX(d)
        val dy = y - SurfaceJoystickMath.centerY(height, d)
        val travel = SurfaceJoystickMath.KNOB_TRAVEL_DP * d
        val axes = SurfaceJoystickMath.fromDisplacement(dx, dy, travel)
        val knob = SurfaceJoystickMath.knobOffset(dx, dy, travel)
        endlessRenderer.setSurfaceMovementInput(axes.first, axes.second)
        onSurfaceStickChanged?.invoke(knob.first, knob.second)
    }

    private fun handleSurfaceTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // ACTION_DOWN starts a *new* gesture stream. If Android lost an
                // old CANCEL (system gesture, display rotation, overlay), do
                // not let its stale pointer continue to walk or aim.
                if (movePointerId != MotionEvent.INVALID_POINTER_ID ||
                    lookPointerId != MotionEvent.INVALID_POINTER_ID) cancelSurfaceControls()
                beginSurfacePointer(event, 0)
            }
            MotionEvent.ACTION_POINTER_DOWN ->
                beginSurfacePointer(event, event.actionIndex)
            MotionEvent.ACTION_MOVE -> {
                val stickIndex = event.findPointerIndex(movePointerId)
                if (stickIndex >= 0) {
                    updateStick(event.getX(stickIndex), event.getY(stickIndex))
                } else if (movePointerId != MotionEvent.INVALID_POINTER_ID) {
                    // Pointer-ID reuse/loss happens around interrupted
                    // multi-touch streams. Never leave walking held.
                    resetSurfaceMovement()
                }
                val lookIndex = event.findPointerIndex(lookPointerId)
                if (lookIndex < 0 && lookPointerId != MotionEvent.INVALID_POINTER_ID) {
                    lookPointerId = MotionEvent.INVALID_POINTER_ID
                }
                if (lookIndex >= 0) {
                    val x = event.getX(lookIndex)
                    val y = event.getY(lookIndex)
                    endlessRenderer.orbitBy(x - lastLookX, y - lastLookY, height)
                    lastLookX = x
                    lastLookY = y
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val leavingId = event.getPointerId(event.actionIndex)
                if (leavingId == movePointerId) {
                    // A second left thumb may already be resting on the
                    // physical stick. Transfer movement to it without an
                    // ACTION_DOWN gap; never capture a right-side look finger.
                    resetSurfaceMovement()
                    for (i in 0 until event.pointerCount) {
                        if (i == event.actionIndex ||
                            event.getPointerId(i) == lookPointerId ||
                            !SurfaceJoystickMath.isInMoveZone(
                                event.getX(i), event.getY(i), height,
                                resources.displayMetrics.density
                            )) continue
                        movePointerId = event.getPointerId(i)
                        updateStick(event.getX(i), event.getY(i))
                        break
                    }
                }
                if (leavingId == lookPointerId) {
                    lookPointerId = MotionEvent.INVALID_POINTER_ID
                    // Another unassigned finger can keep aiming without a jump.
                    for (i in 0 until event.pointerCount) {
                        if (i == event.actionIndex || event.getPointerId(i) == movePointerId ||
                            !SurfaceJoystickMath.isInLookZone(event.getX(i), width)) continue
                        lookPointerId = event.getPointerId(i)
                        lastLookX = event.getX(i)
                        lastLookY = event.getY(i)
                        break
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                // The final thumb-up must commit held travel even if software
                // OpenGL did not draw a frame during the gesture.
                resetSurfaceMovement()
                lookPointerId = MotionEvent.INVALID_POINTER_ID
            }
            MotionEvent.ACTION_CANCEL -> cancelSurfaceControls()
        }
        return true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startFrameTicker()
    }

    override fun onDetachedFromWindow() {
        stopFrameTicker()
        // A paused/detached surface must never keep walking after its touch ends.
        cancelSurfaceControls()
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            // Notification shade, system dialogs and gesture navigation can
            // remove touch focus without delivering ACTION_CANCEL.
            cancelSurfaceControls()
        }
    }

    override fun onPause() {
        stopFrameTicker()
        cancelSurfaceControls()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        startFrameTicker()
    }

    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            // Softer than raw pinch scaling; closer to the HTML OrbitControls feel.
            val adjusted = detector.scaleFactor.toDouble().pow(-0.72).toFloat()
            endlessRenderer.zoomBy(adjusted)
            lastX = detector.focusX
            lastY = detector.focusY
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            lastX = detector.focusX
            lastY = detector.focusY
        }
    })

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            // Picking on release keeps the hit test tied to this gesture. Waiting
            // for double-tap confirmation can pick a different projected body
            // after a subsequent overview drag or Compose label selection.
            endlessRenderer.pick(e.x, e.y)
            return true
        }
    })

    init {
        setEGLContextClientVersion(3)
        // GLSurfaceView's default chooser only requires 16 depth bits.
        // Human-scale stones/shadows over kilometre terrain need 24 bits
        // to avoid quantized depth stripes at grazing viewing angles.
        setEGLConfigChooser(8, 8, 8, 0, 24, 0)
        setRenderer(endlessRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        preserveEGLContextOnPause = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (endlessRenderer.isSurfaceMode()) {
            return handleSurfaceTouch(event)
        }
        if (movePointerId != MotionEvent.INVALID_POINTER_ID ||
            lookPointerId != MotionEvent.INVALID_POINTER_ID) cancelSurfaceControls()
        scaler.onTouchEvent(event)
        gestures.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                lastX = event.x
                lastY = event.y
            }

            MotionEvent.ACTION_MOVE -> {
                if (scaler.isInProgress || event.pointerCount > 1) {
                    // Keep the reference point current while pinching so the first
                    // single-finger move afterwards cannot produce a jump.
                    lastX = event.x
                    lastY = event.y
                } else {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    endlessRenderer.orbitBy(dx, dy, height)
                    lastX = event.x
                    lastY = event.y
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val remainingIndex =
                    if (event.actionIndex == 0 && event.pointerCount > 1) 1 else 0
                lastX = event.getX(remainingIndex)
                lastY = event.getY(remainingIndex)
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                lastX = event.x
                lastY = event.y
            }
        }

        return true
    }
}
