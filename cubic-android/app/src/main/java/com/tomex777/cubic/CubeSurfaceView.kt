package com.tomex777.cubic

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

class CubeSurfaceView(context: Context) : GLSurfaceView(context) {
    private val cubeRenderer = CubeRenderer()
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                cubeRenderer.zoomBy(detector.scaleFactor)
                return true
            }
        }
    )
    private var lastX = 0f
    private var lastY = 0f

    init {
        setEGLContextClientVersion(2)
        setRenderer(cubeRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause = true
    }

    fun setPuzzle(snapshot: PuzzleSnapshot) = cubeRenderer.setPuzzle(snapshot)
    fun setHighlight(axis: Axis?, layer: Int?) = cubeRenderer.setHighlight(axis, layer)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x
                    lastY = event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    cubeRenderer.orbit(
                        (event.x - lastX) * 0.35f,
                        (event.y - lastY) * 0.35f
                    )
                    lastX = event.x
                    lastY = event.y
                }
            }
        }
        return true
    }
}

private class CubeRenderer : GLSurfaceView.Renderer {
    @Volatile private var snapshot = PuzzleState().snapshot()
    @Volatile private var highlightAxis: Axis? = null
    @Volatile private var highlightLayer: Int? = null
    @Volatile private var yaw = -32f
    @Volatile private var pitch = -24f
    @Volatile private var zoom = 1f

    private var program = 0
    private var positionHandle = 0
    private var mvpHandle = 0
    private var colorHandle = 0
    private var viewportWidth = 1
    private var viewportHeight = 1

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val viewModel = FloatArray(16)
    private val mvp = FloatArray(16)

    private val faceBuffers: Map<Direction, FloatBuffer> = mapOf(
        Direction.POS_X to makeBuffer(floatArrayOf(
            .5f,-.5f,-.5f,  .5f,.5f,-.5f,  .5f,.5f,.5f,
            .5f,-.5f,-.5f,  .5f,.5f,.5f,   .5f,-.5f,.5f
        )),
        Direction.NEG_X to makeBuffer(floatArrayOf(
            -.5f,-.5f,.5f,  -.5f,.5f,.5f,  -.5f,.5f,-.5f,
            -.5f,-.5f,.5f,  -.5f,.5f,-.5f, -.5f,-.5f,-.5f
        )),
        Direction.POS_Y to makeBuffer(floatArrayOf(
            -.5f,.5f,-.5f, -.5f,.5f,.5f, .5f,.5f,.5f,
            -.5f,.5f,-.5f, .5f,.5f,.5f,  .5f,.5f,-.5f
        )),
        Direction.NEG_Y to makeBuffer(floatArrayOf(
            -.5f,-.5f,.5f, -.5f,-.5f,-.5f, .5f,-.5f,-.5f,
            -.5f,-.5f,.5f, .5f,-.5f,-.5f,  .5f,-.5f,.5f
        )),
        Direction.POS_Z to makeBuffer(floatArrayOf(
            -.5f,-.5f,.5f, .5f,-.5f,.5f, .5f,.5f,.5f,
            -.5f,-.5f,.5f, .5f,.5f,.5f,  -.5f,.5f,.5f
        )),
        Direction.NEG_Z to makeBuffer(floatArrayOf(
            .5f,-.5f,-.5f, -.5f,-.5f,-.5f, -.5f,.5f,-.5f,
            .5f,-.5f,-.5f, -.5f,.5f,-.5f,  .5f,.5f,-.5f
        ))
    )

    fun setPuzzle(value: PuzzleSnapshot) { snapshot = value }
    fun setHighlight(axis: Axis?, layer: Int?) {
        highlightAxis = axis
        highlightLayer = layer
    }
    fun orbit(dx: Float, dy: Float) {
        yaw += dx
        pitch = (pitch + dy).coerceIn(-80f, 80f)
    }
    fun zoomBy(scaleFactor: Float) {
        zoom = (zoom / scaleFactor).coerceIn(0.6f, 2.2f)
    }

    override fun onSurfaceCreated(
        gl: javax.microedition.khronos.opengles.GL10?,
        config: javax.microedition.khronos.egl.EGLConfig?
    ) {
        GLES20.glClearColor(0.025f, 0.03f, 0.045f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMvp")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
    }

    override fun onSurfaceChanged(
        gl: javax.microedition.khronos.opengles.GL10?,
        width: Int,
        height: Int
    ) {
        viewportWidth = max(1, width)
        viewportHeight = max(1, height)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
    }

    override fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)

        val current = snapshot
        val maxDimension = max(current.width, max(current.height, current.depth)).toFloat()
        val cameraDistance = maxDimension * 2.35f * zoom + 2.2f
        Matrix.setLookAtM(view, 0, 0f, 0f, cameraDistance, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.perspectiveM(
            projection,
            0,
            38f,
            viewportWidth.toFloat() / viewportHeight.toFloat(),
            0.1f,
            100f
        )

        val spacing = 1.06f
        current.cubies.forEach { cubie ->
            Matrix.setIdentityM(model, 0)
            Matrix.rotateM(model, 0, yaw, 0f, 1f, 0f)
            Matrix.rotateM(model, 0, pitch, 1f, 0f, 0f)
            Matrix.translateM(
                model,
                0,
                (cubie.x - (current.width - 1) / 2f) * spacing,
                (cubie.y - (current.height - 1) / 2f) * spacing,
                (cubie.z - (current.depth - 1) / 2f) * spacing
            )
            Matrix.scaleM(model, 0, 0.96f, 0.96f, 0.96f)

            Matrix.multiplyMM(viewModel, 0, view, 0, model, 0)
            Matrix.multiplyMM(mvp, 0, projection, 0, viewModel, 0)
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)

            val highlighted = when (highlightAxis) {
                Axis.X -> cubie.x == highlightLayer
                Axis.Y -> cubie.y == highlightLayer
                Axis.Z -> cubie.z == highlightLayer
                null -> false
            }

            Direction.entries.forEach { direction ->
                val baseColor = cubie.stickers[direction]?.rgba ?: PLASTIC
                val color = if (highlighted) brighten(baseColor) else baseColor
                GLES20.glUniform4fv(colorHandle, 1, color, 0)
                val face = faceBuffers.getValue(direction)
                face.position(0)
                GLES20.glEnableVertexAttribArray(positionHandle)
                GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, face)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
            }
        }
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    private fun brighten(color: FloatArray): FloatArray = floatArrayOf(
        min(1f, color[0] * 1.12f + 0.05f),
        min(1f, color[1] * 1.12f + 0.05f),
        min(1f, color[2] * 1.12f + 0.05f),
        color[3]
    )

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertex)
            GLES20.glAttachShader(it, fragment)
            GLES20.glLinkProgram(it)
        }
    }

    private fun compileShader(type: Int, source: String): Int =
        GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, source)
            GLES20.glCompileShader(it)
        }

    companion object {
        private val PLASTIC = floatArrayOf(0.035f, 0.045f, 0.06f, 1f)

        private fun makeBuffer(values: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(values.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(values)
                    position(0)
                }

        private const val VERTEX_SHADER = """
            uniform mat4 uMvp;
            attribute vec3 aPosition;
            void main() {
                gl_Position = uMvp * vec4(aPosition, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            void main() {
                gl_FragColor = uColor;
            }
        """
    }
}
