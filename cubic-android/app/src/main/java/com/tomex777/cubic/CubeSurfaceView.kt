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
        isClickable = true
        isFocusable = true
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
    @Volatile private var pitch = 24f
    @Volatile private var zoom = 1f

    private var program = 0
    private var positionHandle = 0
    private var mvpHandle = 0
    private var modelHandle = 0
    private var colorHandle = 0
    private var normalHandle = 0
    private var glossHandle = 0
    private var viewportWidth = 1
    private var viewportHeight = 1

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val viewModel = FloatArray(16)
    private val mvp = FloatArray(16)

    private val faceBuffers: Map<Direction, FloatBuffer> = faceMap(0.5f, 0.5f)
    private val stickerBuffers: Map<Direction, FloatBuffer> = faceMap(0.508f, 0.385f)

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
        zoom = (zoom / scaleFactor).coerceIn(0.62f, 2.2f)
    }

    override fun onSurfaceCreated(
        gl: javax.microedition.khronos.opengles.GL10?,
        config: javax.microedition.khronos.egl.EGLConfig?
    ) {
        GLES20.glClearColor(0.018f, 0.023f, 0.035f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMvp")
        modelHandle = GLES20.glGetUniformLocation(program, "uModel")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        normalHandle = GLES20.glGetUniformLocation(program, "uNormal")
        glossHandle = GLES20.glGetUniformLocation(program, "uGloss")
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
        val cameraDistance = maxDimension * 4.35f * zoom + 2.8f

        Matrix.setLookAtM(view, 0, 0f, 0f, cameraDistance, 0f, 0.35f, 0f, 0f, 1f, 0f)
        Matrix.perspectiveM(
            projection,
            0,
            38f,
            viewportWidth.toFloat() / viewportHeight.toFloat(),
            0.1f,
            120f
        )

        val spacing = 1.075f
        current.cubies.forEach { cubie ->
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, 0f, 0.55f, 0f)
            Matrix.rotateM(model, 0, yaw, 0f, 1f, 0f)
            Matrix.rotateM(model, 0, pitch, 1f, 0f, 0f)
            Matrix.translateM(
                model,
                0,
                (cubie.x - (current.width - 1) / 2f) * spacing,
                (cubie.y - (current.height - 1) / 2f) * spacing,
                (cubie.z - (current.depth - 1) / 2f) * spacing
            )
            Matrix.scaleM(model, 0, 0.94f, 0.94f, 0.94f)

            Matrix.multiplyMM(viewModel, 0, view, 0, model, 0)
            Matrix.multiplyMM(mvp, 0, projection, 0, viewModel, 0)
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
            GLES20.glUniformMatrix4fv(modelHandle, 1, false, model, 0)

            val highlighted = when (highlightAxis) {
                Axis.X -> cubie.x == highlightLayer
                Axis.Y -> cubie.y == highlightLayer
                Axis.Z -> cubie.z == highlightLayer
                null -> false
            }

            Direction.entries.forEach { direction ->
                drawFace(
                    direction = direction,
                    buffer = faceBuffers.getValue(direction),
                    color = if (highlighted) PLASTIC_HIGHLIGHT else PLASTIC,
                    gloss = 0.18f
                )
            }

            cubie.stickers.forEach { (direction, sticker) ->
                drawFace(
                    direction = direction,
                    buffer = stickerBuffers.getValue(direction),
                    color = if (highlighted) brighten(sticker.rgba) else sticker.rgba,
                    gloss = 0.55f
                )
            }
        }

        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    private fun drawFace(
        direction: Direction,
        buffer: FloatBuffer,
        color: FloatArray,
        gloss: Float
    ) {
        val normal = NORMALS.getValue(direction)

        GLES20.glUniform4fv(colorHandle, 1, color, 0)
        GLES20.glUniform3f(normalHandle, normal[0], normal[1], normal[2])
        GLES20.glUniform1f(glossHandle, gloss)

        buffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, buffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
    }

    private fun brighten(color: FloatArray): FloatArray = floatArrayOf(
        min(1f, color[0] * 1.10f + 0.08f),
        min(1f, color[1] * 1.10f + 0.08f),
        min(1f, color[2] * 1.10f + 0.08f),
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
        private val PLASTIC = floatArrayOf(0.050f, 0.060f, 0.078f, 1f)
        private val PLASTIC_HIGHLIGHT = floatArrayOf(0.11f, 0.13f, 0.17f, 1f)

        private val NORMALS = mapOf(
            Direction.POS_X to floatArrayOf(1f, 0f, 0f),
            Direction.NEG_X to floatArrayOf(-1f, 0f, 0f),
            Direction.POS_Y to floatArrayOf(0f, 1f, 0f),
            Direction.NEG_Y to floatArrayOf(0f, -1f, 0f),
            Direction.POS_Z to floatArrayOf(0f, 0f, 1f),
            Direction.NEG_Z to floatArrayOf(0f, 0f, -1f)
        )

        private fun makeBuffer(values: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(values.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(values)
                    position(0)
                }

        private fun faceMap(distance: Float, half: Float): Map<Direction, FloatBuffer> = mapOf(
            Direction.POS_X to makeBuffer(floatArrayOf(
                distance,-half,-half,  distance,half,-half,  distance,half,half,
                distance,-half,-half, distance,half,half,   distance,-half,half
            )),
            Direction.NEG_X to makeBuffer(floatArrayOf(
                -distance,-half,half,  -distance,half,half,  -distance,half,-half,
                -distance,-half,half,  -distance,half,-half, -distance,-half,-half
            )),
            Direction.POS_Y to makeBuffer(floatArrayOf(
                -half,distance,-half, -half,distance,half, half,distance,half,
                -half,distance,-half, half,distance,half,  half,distance,-half
            )),
            Direction.NEG_Y to makeBuffer(floatArrayOf(
                -half,-distance,half, -half,-distance,-half, half,-distance,-half,
                -half,-distance,half, half,-distance,-half,  half,-distance,half
            )),
            Direction.POS_Z to makeBuffer(floatArrayOf(
                -half,-half,distance, half,-half,distance, half,half,distance,
                -half,-half,distance, half,half,distance,  -half,half,distance
            )),
            Direction.NEG_Z to makeBuffer(floatArrayOf(
                half,-half,-distance, -half,-half,-distance, -half,half,-distance,
                half,-half,-distance, -half,half,-distance,  half,half,-distance
            ))
        )

        private const val VERTEX_SHADER = """
            uniform mat4 uMvp;
            uniform mat4 uModel;
            uniform vec3 uNormal;
            attribute vec3 aPosition;

            varying vec3 vWorldNormal;

            void main() {
                vWorldNormal = normalize(mat3(uModel) * uNormal);
                gl_Position = uMvp * vec4(aPosition, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;

            uniform vec4 uColor;
            uniform float uGloss;
            varying vec3 vWorldNormal;

            void main() {
                vec3 n = normalize(vWorldNormal);

                vec3 keyLight = normalize(vec3(-0.45, 0.82, 0.55));
                vec3 fillLight = normalize(vec3(0.70, 0.20, -0.55));
                vec3 viewDir = vec3(0.0, 0.0, 1.0);

                float key = max(dot(n, keyLight), 0.0);
                float fill = max(dot(n, fillLight), 0.0) * 0.22;
                float topBounce = max(n.y, 0.0) * 0.14;

                vec3 halfVector = normalize(keyLight + viewDir);
                float specular = pow(max(dot(n, halfVector), 0.0), 28.0) * uGloss;

                float light = 0.34 + key * 0.62 + fill + topBounce;
                vec3 shaded = uColor.rgb * light + vec3(specular);

                gl_FragColor = vec4(clamp(shaded, 0.0, 1.0), uColor.a);
            }
        """
    }
}
