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

class CubeSurfaceView(
    context: Context,
    private val onFrameRendered: (Int) -> Unit = {}
) : GLSurfaceView(context) {
    private val cubeRenderer = CubeRenderer(
        onFrameRendered = { revision ->
            post { onFrameRendered(revision) }
        },
        onAnimationFrameNeeded = {
            post { requestRender() }
        }
    )
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                cubeRenderer.zoomBy(detector.scaleFactor)
                requestRender()
                return true
            }
        }
    )
    private var lastX = 0f
    private var lastY = 0f

    init {
        setEGLContextClientVersion(2)
        setRenderer(cubeRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        preserveEGLContextOnPause = true
        isClickable = true
        isFocusable = true
    }

    fun setPuzzle(
        snapshot: PuzzleSnapshot,
        revision: Int,
        animationFrom: PuzzleSnapshot? = null,
        animationMove: Move? = null
    ) {
        if (
            cubeRenderer.setPuzzle(
                value = snapshot,
                revision = revision,
                animationFrom = animationFrom,
                animationMove = animationMove
            )
        ) {
            requestRender()
        }
    }

    fun setHighlight(axis: Axis?, layer: Int?) {
        if (cubeRenderer.setHighlight(axis, layer)) {
            requestRender()
        }
    }

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
                    requestRender()
                    lastX = event.x
                    lastY = event.y
                }
            }
        }
        return true
    }
}

private data class LitSurface(
    val normal: FloatArray,
    val buffer: FloatBuffer
)

private data class MoveAnimation(
    val from: PuzzleSnapshot,
    val to: PuzzleSnapshot,
    val move: Move,
    val revision: Int,
    val startedNanos: Long = System.nanoTime()
)

private class CubeRenderer(
    private val onFrameRendered: (Int) -> Unit,
    private val onAnimationFrameNeeded: () -> Unit
) : GLSurfaceView.Renderer {
    @Volatile private var snapshot = PuzzleState().snapshot()
    @Volatile private var requestedRevision = -1
    @Volatile private var moveAnimation: MoveAnimation? = null
    private var reportedRevision = Int.MIN_VALUE
    @Volatile private var highlightAxis: Axis? = null
    @Volatile private var highlightLayer: Int? = null
    @Volatile private var yaw = -34f
    @Volatile private var pitch = 26f
    @Volatile private var zoom = 1f

    private var program = 0
    private var positionHandle = 0
    private var mvpHandle = 0
    private var modelViewHandle = 0
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

    private val faceBuffers: Map<Direction, FloatBuffer> = faceMap(0.5f, 0.44f)
    private val stickerBuffers: Map<Direction, FloatBuffer> = faceMap(0.507f, 0.385f)
    private val bevelSurfaces: List<LitSurface> =
        createBevelSurfaces(outer = 0.5f, inner = 0.44f)

    fun setPuzzle(
        value: PuzzleSnapshot,
        revision: Int,
        animationFrom: PuzzleSnapshot?,
        animationMove: Move?
    ): Boolean {
        if (requestedRevision == revision) return false

        snapshot = value
        requestedRevision = revision
        moveAnimation = if (animationFrom != null && animationMove != null) {
            MoveAnimation(
                from = animationFrom,
                to = value,
                move = animationMove,
                revision = revision
            )
        } else {
            null
        }
        return true
    }

    fun setHighlight(axis: Axis?, layer: Int?): Boolean {
        if (highlightAxis == axis && highlightLayer == layer) return false
        highlightAxis = axis
        highlightLayer = layer
        return true
    }

    fun orbit(dx: Float, dy: Float) {
        yaw += dx
        pitch = (pitch + dy).coerceIn(-80f, 80f)
    }

    fun zoomBy(scaleFactor: Float) {
        zoom = (zoom / scaleFactor).coerceIn(0.58f, 2.2f)
    }

    override fun onSurfaceCreated(
        gl: javax.microedition.khronos.opengles.GL10?,
        config: javax.microedition.khronos.egl.EGLConfig?
    ) {
        reportedRevision = Int.MIN_VALUE
        moveAnimation = null
        GLES20.glClearColor(0.012f, 0.017f, 0.028f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMvp")
        modelViewHandle = GLES20.glGetUniformLocation(program, "uModelView")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        normalHandle = GLES20.glGetUniformLocation(program, "uFaceNormal")
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

        val activeAnimation = moveAnimation
        val linearProgress = activeAnimation?.let { animation ->
            ((System.nanoTime() - animation.startedNanos).toDouble() /
                ANIMATION_DURATION_NANOS.toDouble()).toFloat().coerceIn(0f, 1f)
        } ?: 1f
        val animating = activeAnimation != null && linearProgress < 1f

        if (activeAnimation != null && !animating) {
            moveAnimation = null
        }

        val current = if (animating) activeAnimation!!.from else snapshot
        val animatedMove = if (animating) activeAnimation!!.move else null
        val easedProgress = linearProgress * linearProgress * (3f - 2f * linearProgress)
        val animatedAngle = animatedMove?.quarterTurns?.times(90f)?.times(easedProgress) ?: 0f

        val maxDimension = max(current.width, max(current.height, current.depth)).toFloat()
        val cameraDistance = maxDimension * 3.55f * zoom + 2.35f

        Matrix.setLookAtM(
            view,
            0,
            0f,
            0f,
            cameraDistance,
            0f,
            0f,
            0f,
            0f,
            1f,
            0f
        )
        Matrix.perspectiveM(
            projection,
            0,
            41f,
            viewportWidth.toFloat() / viewportHeight.toFloat(),
            0.1f,
            120f
        )

        val spacing = 1.065f
        current.cubies.forEach { cubie ->
            if (cubie.stickers.isEmpty()) return@forEach

            val moving = animatedMove?.let { belongsToMove(cubie, it) } == true

            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, 0f, 0.28f, 0f)
            Matrix.rotateM(model, 0, yaw, 0f, 1f, 0f)
            Matrix.rotateM(model, 0, pitch, 1f, 0f, 0f)

            if (moving) {
                when (animatedMove!!.axis) {
                    Axis.X -> Matrix.rotateM(model, 0, animatedAngle, 1f, 0f, 0f)
                    Axis.Y -> Matrix.rotateM(model, 0, animatedAngle, 0f, 1f, 0f)
                    Axis.Z -> Matrix.rotateM(model, 0, animatedAngle, 0f, 0f, 1f)
                }
            }

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
            GLES20.glUniformMatrix4fv(modelViewHandle, 1, false, viewModel, 0)

            val highlighted = if (animatedMove != null) {
                moving
            } else {
                when (highlightAxis) {
                    Axis.X -> cubie.x == highlightLayer
                    Axis.Y -> cubie.y == highlightLayer
                    Axis.Z -> cubie.z == highlightLayer
                    null -> false
                }
            }

            val bodyColor = if (highlighted) PLASTIC_HIGHLIGHT else PLASTIC

            Direction.entries.forEach { direction ->
                drawFace(
                    direction = direction,
                    buffer = faceBuffers.getValue(direction),
                    color = bodyColor,
                    gloss = if (highlighted) 0.40f else 0.30f
                )
            }

            bevelSurfaces.forEach { surface ->
                drawSurface(
                    normal = surface.normal,
                    buffer = surface.buffer,
                    color = bodyColor,
                    gloss = if (highlighted) 0.58f else 0.48f
                )
            }

            cubie.stickers.forEach { (direction, sticker) ->
                drawFace(
                    direction = direction,
                    buffer = stickerBuffers.getValue(direction),
                    color = if (highlighted) brighten(sticker.rgba) else sticker.rgba,
                    gloss = if (highlighted) 0.42f else 0.32f
                )
            }
        }

        GLES20.glDisableVertexAttribArray(positionHandle)

        if (!animating) {
            GLES20.glFinish()
        }

        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            throw IllegalStateException(
                "Cubic OpenGL frame failed with error 0x${error.toString(16)}"
            )
        }

        if (animating) {
            onAnimationFrameNeeded()
        } else {
            val revisionToReport = requestedRevision
            if (revisionToReport != reportedRevision) {
                reportedRevision = revisionToReport
                onFrameRendered(revisionToReport)
            }
        }
    }

    private fun belongsToMove(cubie: CubieSnapshot, move: Move): Boolean = when (move.axis) {
        Axis.X -> cubie.x == move.layer
        Axis.Y -> cubie.y == move.layer
        Axis.Z -> cubie.z == move.layer
    }

    private fun drawFace(
        direction: Direction,
        buffer: FloatBuffer,
        color: FloatArray,
        gloss: Float
    ) = drawSurface(
        normal = normalFor(direction),
        buffer = buffer,
        color = color,
        gloss = gloss
    )

    private fun drawSurface(
        normal: FloatArray,
        buffer: FloatBuffer,
        color: FloatArray,
        gloss: Float
    ) {
        GLES20.glUniform4fv(colorHandle, 1, color, 0)
        GLES20.glUniform3fv(normalHandle, 1, normal, 0)
        GLES20.glUniform1f(glossHandle, gloss)

        buffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(
            positionHandle,
            3,
            GLES20.GL_FLOAT,
            false,
            0,
            buffer
        )
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, buffer.capacity() / 3)
    }

    private fun normalFor(direction: Direction): FloatArray = when (direction) {
        Direction.POS_X -> NORMAL_POS_X
        Direction.NEG_X -> NORMAL_NEG_X
        Direction.POS_Y -> NORMAL_POS_Y
        Direction.NEG_Y -> NORMAL_NEG_Y
        Direction.POS_Z -> NORMAL_POS_Z
        Direction.NEG_Z -> NORMAL_NEG_Z
    }

    private fun brighten(color: FloatArray): FloatArray = floatArrayOf(
        min(1f, color[0] * 1.10f + 0.06f),
        min(1f, color[1] * 1.10f + 0.06f),
        min(1f, color[2] * 1.10f + 0.06f),
        color[3]
    )

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()

        GLES20.glAttachShader(program, vertex)
        GLES20.glAttachShader(program, fragment)
        GLES20.glLinkProgram(program)

        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw IllegalStateException("Cubic shader link failed: $log")
        }

        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("Cubic shader compile failed: $log")
        }

        return shader
    }

    companion object {
        private const val ANIMATION_DURATION_NANOS = 160_000_000L

        private val PLASTIC = floatArrayOf(0.052f, 0.062f, 0.082f, 1f)
        private val PLASTIC_HIGHLIGHT = floatArrayOf(0.105f, 0.125f, 0.165f, 1f)

        private val NORMAL_POS_X = floatArrayOf(1f, 0f, 0f)
        private val NORMAL_NEG_X = floatArrayOf(-1f, 0f, 0f)
        private val NORMAL_POS_Y = floatArrayOf(0f, 1f, 0f)
        private val NORMAL_NEG_Y = floatArrayOf(0f, -1f, 0f)
        private val NORMAL_POS_Z = floatArrayOf(0f, 0f, 1f)
        private val NORMAL_NEG_Z = floatArrayOf(0f, 0f, -1f)

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


        private fun quad(
            a: FloatArray,
            b: FloatArray,
            c: FloatArray,
            d: FloatArray
        ): FloatBuffer = makeBuffer(
            floatArrayOf(
                a[0], a[1], a[2],
                b[0], b[1], b[2],
                c[0], c[1], c[2],
                a[0], a[1], a[2],
                c[0], c[1], c[2],
                d[0], d[1], d[2]
            )
        )

        private fun triangle(
            a: FloatArray,
            b: FloatArray,
            c: FloatArray
        ): FloatBuffer = makeBuffer(
            floatArrayOf(
                a[0], a[1], a[2],
                b[0], b[1], b[2],
                c[0], c[1], c[2]
            )
        )

        private fun normalized(x: Float, y: Float, z: Float): FloatArray {
            val length = kotlin.math.sqrt(x * x + y * y + z * z)
            return floatArrayOf(x / length, y / length, z / length)
        }

        private fun createBevelSurfaces(
            outer: Float,
            inner: Float
        ): List<LitSurface> {
            val surfaces = mutableListOf<LitSurface>()

            listOf(-1f, 1f).forEach { sx ->
                listOf(-1f, 1f).forEach { sy ->
                    surfaces += LitSurface(
                        normalized(sx, sy, 0f),
                        quad(
                            floatArrayOf(sx * outer, sy * inner, -inner),
                            floatArrayOf(sx * outer, sy * inner, inner),
                            floatArrayOf(sx * inner, sy * outer, inner),
                            floatArrayOf(sx * inner, sy * outer, -inner)
                        )
                    )
                }
            }

            listOf(-1f, 1f).forEach { sx ->
                listOf(-1f, 1f).forEach { sz ->
                    surfaces += LitSurface(
                        normalized(sx, 0f, sz),
                        quad(
                            floatArrayOf(sx * outer, -inner, sz * inner),
                            floatArrayOf(sx * outer, inner, sz * inner),
                            floatArrayOf(sx * inner, inner, sz * outer),
                            floatArrayOf(sx * inner, -inner, sz * outer)
                        )
                    )
                }
            }

            listOf(-1f, 1f).forEach { sy ->
                listOf(-1f, 1f).forEach { sz ->
                    surfaces += LitSurface(
                        normalized(0f, sy, sz),
                        quad(
                            floatArrayOf(-inner, sy * outer, sz * inner),
                            floatArrayOf(inner, sy * outer, sz * inner),
                            floatArrayOf(inner, sy * inner, sz * outer),
                            floatArrayOf(-inner, sy * inner, sz * outer)
                        )
                    )
                }
            }

            listOf(-1f, 1f).forEach { sx ->
                listOf(-1f, 1f).forEach { sy ->
                    listOf(-1f, 1f).forEach { sz ->
                        surfaces += LitSurface(
                            normalized(sx, sy, sz),
                            triangle(
                                floatArrayOf(sx * outer, sy * inner, sz * inner),
                                floatArrayOf(sx * inner, sy * outer, sz * inner),
                                floatArrayOf(sx * inner, sy * inner, sz * outer)
                            )
                        )
                    }
                }
            }

            return surfaces
        }

        private const val VERTEX_SHADER = """
            uniform mat4 uMvp;
            uniform mat4 uModelView;
            uniform vec3 uFaceNormal;
            attribute vec3 aPosition;

            varying vec3 vNormal;
            varying vec3 vViewPosition;

            void main() {
                vec4 viewPosition = uModelView * vec4(aPosition, 1.0);
                vViewPosition = viewPosition.xyz;
                vNormal = normalize((uModelView * vec4(uFaceNormal, 0.0)).xyz);
                gl_Position = uMvp * vec4(aPosition, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;

            uniform vec4 uColor;
            uniform float uGloss;

            varying vec3 vNormal;
            varying vec3 vViewPosition;

            void main() {
                vec3 normal = normalize(vNormal);
                vec3 viewDirection = normalize(-vViewPosition);

                vec3 keyLight = normalize(vec3(-0.45, 0.78, 0.62));
                vec3 fillLight = normalize(vec3(0.72, 0.24, 0.42));

                float keyDiffuse = max(dot(normal, keyLight), 0.0);
                float fillDiffuse = max(dot(normal, fillLight), 0.0);

                vec3 halfVector = normalize(keyLight + viewDirection);
                float specular =
                    pow(max(dot(normal, halfVector), 0.0), 34.0) * uGloss;

                float rim =
                    pow(1.0 - max(dot(normal, viewDirection), 0.0), 2.0) * 0.10;

                float light = 0.30 + keyDiffuse * 0.72 + fillDiffuse * 0.22;
                vec3 litColor = uColor.rgb * light;
                litColor += vec3(specular);
                litColor += uColor.rgb * rim;

                gl_FragColor = vec4(min(litColor, vec3(1.0)), uColor.a);
            }
        """
    }
}
