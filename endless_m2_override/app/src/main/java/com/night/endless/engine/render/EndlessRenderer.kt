package com.night.endless.engine.render

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.night.endless.engine.collision.CollisionWorld
import com.night.endless.engine.collision.SphereCollider
import com.night.endless.engine.math.Vec3d
import com.night.endless.engine.scene.CelestialBody
import com.night.endless.engine.scene.UniverseClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class BodyLabelSnapshot(
    val id: String,
    val name: String,
    val xPx: Float,
    val yPx: Float,
    val visible: Boolean
)

class EndlessRenderer(
    private val onSelectionChanged: (String?) -> Unit
) : GLSurfaceView.Renderer {
    private val bodies = mutableListOf<CelestialBody>()
    private val byId get() = bodies.associateBy { it.id }
    private val clock = UniverseClock()
    private val collision = CollisionWorld()

    private lateinit var sphere: SphereMesh
    private var program = 0
    private var starProgram = 0
    private var lineProgram = 0
    private var starBuffer: FloatBuffer? = null
    private var starCount = 0
    private val orbitBuffers = mutableMapOf<String, FloatBuffer>()
    private val orbitCounts = mutableMapOf<String, Int>()
    private var width = 1
    private var height = 1

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)

    private var lastNanos = System.nanoTime()
    private var yaw = 0.72
    private var pitch = 0.28
    private var distance = 5.0
    private var selectedId: String? = "earth"
    private var overview = false
    private var showOrbits = true
    private var savedFocus: CameraState? = null
    private var cameraPosition = Vec3d(0.0, 8.0, 38.0)
    private var previousCameraPosition = cameraPosition
    private var cameraTarget = Vec3d.ZERO
    private val cameraRadius = 0.12

    @Volatile private var latestLabels: List<BodyLabelSnapshot> = emptyList()

    data class CameraState(val selectedId: String?, val yaw: Double, val pitch: Double, val distance: Double)

    init {
        bodies += CelestialBody("sun", "Sun", 2.35, 0.0, 1.0, 609.12, floatArrayOf(1.0f, .63f, .15f, 1f), radiusKm = 696340.0)
        bodies += CelestialBody("mercury", "Mercury", .34, 5.0, 87.969, 1407.6, floatArrayOf(.60f,.57f,.54f,1f), phaseRad=.2, radiusKm=2439.7, semiMajorAxisAu=.3871)
        bodies += CelestialBody("venus", "Venus", .53, 7.2, 224.701, -5832.5, floatArrayOf(.84f,.59f,.31f,1f), phaseRad=1.5, radiusKm=6051.8, semiMajorAxisAu=.7233)
        bodies += CelestialBody("earth", "Earth", .56, 9.5, 365.256, 23.9345, floatArrayOf(.18f,.44f,.86f,1f), phaseRad=2.5, radiusKm=6371.0, semiMajorAxisAu=1.0)
        bodies += CelestialBody("moon", "Moon", .18, 1.15, 27.3217, 655.7, floatArrayOf(.69f,.69f,.67f,1f), parentId="earth", phaseRad=1.1, radiusKm=1737.4)
        bodies += CelestialBody("mars", "Mars", .42, 12.3, 686.98, 24.6229, floatArrayOf(.72f,.24f,.10f,1f), phaseRad=.8, radiusKm=3389.5, semiMajorAxisAu=1.5237)
        bodies += CelestialBody("jupiter", "Jupiter", 1.22, 17.2, 4332.589, 9.925, floatArrayOf(.72f,.58f,.42f,1f), phaseRad=2.1, radiusKm=69911.0, semiMajorAxisAu=5.2029)
        bodies += CelestialBody("saturn", "Saturn", 1.02, 22.5, 10759.22, 10.656, floatArrayOf(.79f,.68f,.43f,1f), phaseRad=2.7, radiusKm=58232.0, semiMajorAxisAu=9.5371)
        bodies += CelestialBody("uranus", "Uranus", .72, 27.6, 30688.5, -17.24, floatArrayOf(.42f,.78f,.82f,1f), phaseRad=1.7, radiusKm=25362.0, semiMajorAxisAu=19.191)
        bodies += CelestialBody("neptune", "Neptune", .70, 32.8, 60182.0, 16.11, floatArrayOf(.22f,.38f,.84f,1f), phaseRad=.4, radiusKm=24622.0, semiMajorAxisAu=30.07)
        updateBodyPositions(0.0)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.004f, 0.006f, 0.02f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        sphere = SphereMesh()
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        starProgram = createProgram(STAR_VERTEX_SHADER, STAR_FRAGMENT_SHADER)
        lineProgram = createProgram(LINE_VERTEX_SHADER, LINE_FRAGMENT_SHADER)
        buildStars()
        buildOrbitBuffers()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = max(1, w)
        height = max(1, h)
        GLES30.glViewport(0, 0, width, height)
        Matrix.perspectiveM(projection, 0, 48f, width.toFloat() / height, 0.03f, 300f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastNanos) / 1_000_000_000.0).coerceIn(0.0, .05)
        lastNanos = now
        val simSeconds = clock.advance(dt)
        updateBodyPositions(simSeconds)
        updateCamera()
        updateLabelSnapshots()

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        drawStars()
        if (showOrbits) drawOrbits()
        bodies.forEach(::drawBody)
        drawSaturnRings()
    }

    @Synchronized
    fun orbitBy(dx: Float, dy: Float) {
        yaw -= dx * 0.006
        pitch = (pitch - dy * 0.006).coerceIn(-1.42, 1.42)
    }

    @Synchronized
    fun zoomBy(scaleFactor: Float) {
        if (!scaleFactor.isFinite() || scaleFactor <= 0f) return
        distance = (distance * scaleFactor).coerceIn(.35, 95.0)
    }

    @Synchronized
    fun togglePause(): Boolean {
        clock.paused = !clock.paused
        return clock.paused
    }

    @Synchronized
    fun resetTime() = clock.reset()

    @Synchronized
    fun currentTimeMillis(): Long = clock.currentTimeMillis()

    @Synchronized
    fun speedLabel(): String = clock.speedLabel()

    @Synchronized
    fun slower() { clock.slower() }

    @Synchronized
    fun faster() { clock.faster() }

    @Synchronized
    fun toggleOrbits(): Boolean {
        showOrbits = !showOrbits
        return showOrbits
    }

    fun labelSnapshots(): List<BodyLabelSnapshot> = latestLabels

    @Synchronized
    fun toggleOverview(): Boolean {
        if (!overview) {
            savedFocus = CameraState(selectedId, yaw, pitch, distance)
            overview = true
            selectedId = null
            yaw = .72
            pitch = .38
            distance = 43.0
            onSelectionChanged(null)
        } else {
            val s = savedFocus
            overview = false
            if (s != null) {
                selectedId = s.selectedId
                yaw = s.yaw
                pitch = s.pitch
                distance = s.distance
            } else {
                selectedId = "earth"
                distance = 5.0
            }
            onSelectionChanged(selectedId)
        }
        return overview
    }

    @Synchronized
    fun focus(id: String) {
        val body = bodies.firstOrNull { it.id == id } ?: return
        overview = false
        selectedId = id
        distance = max(body.radius * 5.5, 1.4)
        yaw = .72
        pitch = .28
        onSelectionChanged(id)
    }

    @Synchronized
    fun pick(screenX: Float, screenY: Float) {
        val inv = FloatArray(16)
        if (!Matrix.invertM(inv, 0, viewProjection, 0)) return
        val x = (2f * screenX / width) - 1f
        val y = 1f - (2f * screenY / height)
        val near = unproject(inv, x, y, -1f)
        val far = unproject(inv, x, y, 1f)
        val origin = Vec3d(near[0].toDouble(), near[1].toDouble(), near[2].toDouble())
        val dir = (Vec3d(far[0].toDouble(), far[1].toDouble(), far[2].toDouble()) - origin).normalized()

        var best: Pair<CelestialBody, Double>? = null
        for (body in bodies) {
            val oc = origin - body.position
            val b = oc.dot(dir)
            val c = oc.dot(oc) - body.radius * body.radius
            val d = b * b - c
            if (d < 0.0) continue
            val t = -b - sqrt(d)
            if (t > 0.0 && (best == null || t < best!!.second)) best = body to t
        }
        best?.first?.let { focus(it.id) }
    }

    @Synchronized
    private fun updateCamera() {
        val target = selectedId?.let { byId[it]?.position } ?: Vec3d.ZERO
        cameraTarget = target
        val cp = cos(pitch)
        val desired = target + Vec3d(
            cos(yaw) * cp * distance,
            sin(pitch) * distance,
            sin(yaw) * cp * distance
        )

        val motion = desired - previousCameraPosition
        val resolved = collision.resolveMotion(previousCameraPosition, motion, cameraRadius, skin = .025)
        cameraPosition = resolved.position

        selectedId?.let { id ->
            byId[id]?.let { body ->
                val radial = cameraPosition - body.position
                val minDistance = body.radius + cameraRadius + .04
                if (radial.length() < minDistance) {
                    val normal = if (radial.length() < 1e-8) Vec3d(0.0, 0.0, 1.0) else radial.normalized()
                    cameraPosition = body.position + normal * minDistance
                }
            }
        }
        previousCameraPosition = cameraPosition

        Matrix.setLookAtM(
            view, 0,
            cameraPosition.x.toFloat(), cameraPosition.y.toFloat(), cameraPosition.z.toFloat(),
            target.x.toFloat(), target.y.toFloat(), target.z.toFloat(),
            0f, 1f, 0f
        )
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)
    }

    private fun updateBodyPositions(simSeconds: Double) {
        val days = simSeconds / 86400.0
        val map = HashMap<String, CelestialBody>()
        bodies.forEach { body ->
            if (body.id == "sun") {
                body.position = Vec3d.ZERO
            } else {
                val angle = body.phaseRad + (2.0 * PI * days / body.orbitPeriodDays)
                val local = Vec3d(cos(angle) * body.orbitRadius, 0.0, sin(angle) * body.orbitRadius)
                body.position = body.parentId?.let { parent -> (map[parent]?.position ?: Vec3d.ZERO) + local } ?: local
            }
            map[body.id] = body
        }
        collision.replaceSpheres(bodies.map { SphereCollider(it.id, it.position, it.radius) })
    }

    private fun updateLabelSnapshots() {
        val out = ArrayList<BodyLabelSnapshot>(bodies.size)
        for (body in bodies) {
            val input = floatArrayOf(body.position.x.toFloat(), body.position.y.toFloat(), body.position.z.toFloat(), 1f)
            val clip = FloatArray(4)
            Matrix.multiplyMV(clip, 0, viewProjection, 0, input, 0)
            val w = clip[3]
            if (w <= 0.0001f) {
                out += BodyLabelSnapshot(body.id, body.name, 0f, 0f, false)
                continue
            }
            val nx = clip[0] / w
            val ny = clip[1] / w
            val visible = nx in -1.15f..1.15f && ny in -1.15f..1.15f
            val sx = (nx * .5f + .5f) * width
            val sy = (1f - (ny * .5f + .5f)) * height
            out += BodyLabelSnapshot(body.id, body.name, sx, sy, visible)
        }
        latestLabels = out
    }

    private fun drawBody(body: CelestialBody) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, body.position.x.toFloat(), body.position.y.toFloat(), body.position.z.toFloat())
        Matrix.scaleM(model, 0, body.radius.toFloat(), body.radius.toFloat(), body.radius.toFloat())
        Matrix.multiplyMM(mvp, 0, viewProjection, 0, model, 0)

        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program, "uMvp"), 1, false, mvp, 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program, "uModel"), 1, false, model, 0)
        GLES30.glUniform4fv(GLES30.glGetUniformLocation(program, "uColor"), 1, body.color, 0)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(program, "uCamera"), cameraPosition.x.toFloat(), cameraPosition.y.toFloat(), cameraPosition.z.toFloat())
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uEmissive"), if (body.id == "sun") 1f else 0f)

        val stride = 6 * 4
        sphere.vertices.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, sphere.vertices)
        sphere.vertices.position(3)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, sphere.vertices)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, sphere.indexCount, GLES30.GL_UNSIGNED_SHORT, sphere.indices)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
    }

    private fun buildOrbitBuffers() {
        for (body in bodies) {
            if (body.id == "sun") continue
            val segments = if (body.id == "moon") 96 else 180
            val values = FloatArray(segments * 3)
            for (i in 0 until segments) {
                val a = i.toDouble() / segments.toDouble() * PI * 2.0
                values[i * 3] = (cos(a) * body.orbitRadius).toFloat()
                values[i * 3 + 1] = 0f
                values[i * 3 + 2] = (sin(a) * body.orbitRadius).toFloat()
            }
            orbitBuffers[body.id] = floatBuffer(values)
            orbitCounts[body.id] = segments
        }
    }

    private fun drawOrbits() {
        GLES30.glUseProgram(lineProgram)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(lineProgram, "uVp"), 1, false, viewProjection, 0)
        GLES30.glLineWidth(1f)
        for (body in bodies) {
            if (body.id == "sun") continue
            val buffer = orbitBuffers[body.id] ?: continue
            val offset = body.parentId?.let { byId[it]?.position } ?: Vec3d.ZERO
            GLES30.glUniform3f(GLES30.glGetUniformLocation(lineProgram, "uOffset"), offset.x.toFloat(), offset.y.toFloat(), offset.z.toFloat())
            GLES30.glUniform4f(
                GLES30.glGetUniformLocation(lineProgram, "uColor"),
                body.color[0] * .62f, body.color[1] * .62f, body.color[2] * .62f, .34f
            )
            buffer.position(0)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, buffer)
            GLES30.glDrawArrays(GLES30.GL_LINE_LOOP, 0, orbitCounts[body.id] ?: 0)
            GLES30.glDisableVertexAttribArray(0)
        }
    }

    private fun drawSaturnRings() {
        val saturn = byId["saturn"] ?: return
        GLES30.glUseProgram(lineProgram)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(lineProgram, "uVp"), 1, false, viewProjection, 0)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(lineProgram, "uOffset"), saturn.position.x.toFloat(), saturn.position.y.toFloat(), saturn.position.z.toFloat())
        val radii = floatArrayOf(1.34f, 1.48f, 1.62f, 1.80f, 2.02f)
        radii.forEachIndexed { index, multiplier ->
            val values = FloatArray(128 * 3)
            val r = saturn.radius.toFloat() * multiplier
            for (i in 0 until 128) {
                val a = i.toDouble() / 128.0 * PI * 2.0
                values[i * 3] = (cos(a) * r).toFloat()
                values[i * 3 + 1] = 0f
                values[i * 3 + 2] = (sin(a) * r).toFloat()
            }
            val b = floatBuffer(values)
            GLES30.glUniform4f(GLES30.glGetUniformLocation(lineProgram, "uColor"), .83f, .73f, .52f, .30f + index * .06f)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, b)
            GLES30.glDrawArrays(GLES30.GL_LINE_LOOP, 0, 128)
            GLES30.glDisableVertexAttribArray(0)
        }
    }

    private fun buildStars() {
        val count = 4200
        val values = FloatArray(count * 3)
        var seed = 0x1234ABCDL
        fun rnd(): Double {
            seed = (seed * 1664525L + 1013904223L) and 0xffffffffL
            return seed.toDouble() / 0xffffffffL.toDouble()
        }
        for (i in 0 until count) {
            val z = rnd() * 2.0 - 1.0
            val a = rnd() * 2.0 * PI
            val radius = 115.0 + rnd() * 20.0
            val q = sqrt(1.0 - z * z)
            values[i*3] = (radius * q * cos(a)).toFloat()
            values[i*3+1] = (z * radius).toFloat()
            values[i*3+2] = (radius * q * sin(a)).toFloat()
        }
        starBuffer = floatBuffer(values)
        starCount = count
    }

    private fun drawStars() {
        GLES30.glDepthMask(false)
        GLES30.glUseProgram(starProgram)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(starProgram, "uVp"), 1, false, viewProjection, 0)
        val b = starBuffer ?: return
        b.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, b)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, starCount)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDepthMask(true)
    }

    private fun floatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(values)
            position(0)
        }

    private fun unproject(inv: FloatArray, x: Float, y: Float, z: Float): FloatArray {
        val input = floatArrayOf(x, y, z, 1f)
        val output = FloatArray(4)
        Matrix.multiplyMV(output, 0, inv, 0, input, 0)
        val w = output[3].takeIf { kotlin.math.abs(it) > 1e-7f } ?: 1f
        return floatArrayOf(output[0]/w, output[1]/w, output[2]/w)
    }

    private fun createProgram(vs: String, fs: String): Int {
        fun shader(type: Int, source: String): Int {
            val id = GLES30.glCreateShader(type)
            GLES30.glShaderSource(id, source)
            GLES30.glCompileShader(id)
            val status = IntArray(1)
            GLES30.glGetShaderiv(id, GLES30.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) error(GLES30.glGetShaderInfoLog(id))
            return id
        }
        val vertex = shader(GLES30.GL_VERTEX_SHADER, vs)
        val fragment = shader(GLES30.GL_FRAGMENT_SHADER, fs)
        return GLES30.glCreateProgram().also { p ->
            GLES30.glAttachShader(p, vertex)
            GLES30.glAttachShader(p, fragment)
            GLES30.glLinkProgram(p)
            val status = IntArray(1)
            GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) error(GLES30.glGetProgramInfoLog(p))
            GLES30.glDeleteShader(vertex)
            GLES30.glDeleteShader(fragment)
        }
    }

    companion object {
        private const val VERTEX_SHADER = """#version 300 es
layout(location=0) in vec3 aPosition;
layout(location=1) in vec3 aNormal;
uniform mat4 uMvp;
uniform mat4 uModel;
out vec3 vNormal;
out vec3 vWorld;
void main(){ vec4 world=uModel*vec4(aPosition,1.0); vWorld=world.xyz; vNormal=normalize(mat3(uModel)*aNormal); gl_Position=uMvp*vec4(aPosition,1.0); }
"""
        private const val FRAGMENT_SHADER = """#version 300 es
precision highp float;
in vec3 vNormal;
in vec3 vWorld;
uniform vec4 uColor;
uniform vec3 uCamera;
uniform float uEmissive;
out vec4 fragColor;
void main(){
    vec3 N=normalize(vNormal);
    vec3 L=normalize(-vWorld);
    vec3 V=normalize(uCamera-vWorld);
    float ndl=max(dot(N,L),0.0);
    float rim=pow(1.0-max(dot(N,V),0.0),3.0);
    float light=mix(0.10+0.90*ndl,1.0,uEmissive);
    vec3 color=uColor.rgb*light + uColor.rgb*rim*0.10*(1.0-uEmissive);
    fragColor=vec4(color,uColor.a);
}
"""
        private const val STAR_VERTEX_SHADER = """#version 300 es
layout(location=0) in vec3 aPosition;
uniform mat4 uVp;
void main(){ gl_Position=uVp*vec4(aPosition,1.0); gl_PointSize=2.0; }
"""
        private const val STAR_FRAGMENT_SHADER = """#version 300 es
precision mediump float;
out vec4 fragColor;
void main(){ vec2 p=gl_PointCoord-vec2(.5); if(dot(p,p)>.25) discard; float a=1.0-smoothstep(.08,.25,dot(p,p)); fragColor=vec4(.82,.88,1.0,.72*a); }
"""
        private const val LINE_VERTEX_SHADER = """#version 300 es
layout(location=0) in vec3 aPosition;
uniform mat4 uVp;
uniform vec3 uOffset;
void main(){ gl_Position=uVp*vec4(aPosition+uOffset,1.0); }
"""
        private const val LINE_FRAGMENT_SHADER = """#version 300 es
precision mediump float;
uniform vec4 uColor;
out vec4 fragColor;
void main(){ fragColor=uColor; }
"""
    }
}
