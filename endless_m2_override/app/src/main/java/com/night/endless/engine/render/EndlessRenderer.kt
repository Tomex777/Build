package com.night.endless.engine.render

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
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
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
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
    private val context: Context,
    private val onSelectionChanged: (String?) -> Unit
) : GLSurfaceView.Renderer {

    private val bodies = mutableListOf<CelestialBody>()
    private val clock = UniverseClock()
    private val collision = CollisionWorld()
    private val byId get() = bodies.associateBy { it.id }

    private lateinit var sphere: SphereMesh
    private var planetProgram = 0
    private var starProgram = 0
    private var lineProgram = 0
    private var ringProgram = 0

    private val textures = mutableMapOf<String, Int>()
    private var earthCloudsTexture = 0
    private var earthNightTexture = 0
    private var venusAtmosphereTexture = 0
    private var saturnRingTexture = 0

    private var starBuffer: FloatBuffer? = null
    private var starCount = 0
    private val orbitBuffers = mutableMapOf<String, FloatBuffer>()
    private val orbitCounts = mutableMapOf<String, Int>()
    private var ringBuffer: FloatBuffer? = null
    private var ringVertexCount = 0

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
    private var targetDistance = 5.0

    private var pendingYaw = 0.0
    private var pendingPitch = 0.0
    private val dampingFactor = 0.055
    private val rotateSpeedFocused = 0.26
    private val rotateSpeedOverview = 0.20

    private var selectedId: String? = "earth"
    private var overview = false
    private var showOrbits = true
    private var savedFocus: CameraState? = null

    private var cameraPosition = Vec3d(0.0, 8.0, 38.0)
    private var previousCameraPosition = cameraPosition
    private var cameraTarget = Vec3d.ZERO
    private val cameraRadius = 0.12

    private var cloudRotation = 0.0
    private var venusCloudRotation = 0.0

    @Volatile
    private var latestLabels: List<BodyLabelSnapshot> = emptyList()

    data class CameraState(
        val selectedId: String?,
        val yaw: Double,
        val pitch: Double,
        val distance: Double,
        val targetDistance: Double
    )

    init {
        bodies += CelestialBody(
            "sun", "Sun", 2.35, 0.0, 1.0, 609.12,
            floatArrayOf(1.0f, .63f, .15f, 1f),
            radiusKm = 696340.0, axialTiltDeg = 7.25
        )
        bodies += CelestialBody(
            "mercury", "Mercury", .34, 5.0, 87.969, 1407.6,
            floatArrayOf(.60f, .57f, .54f, 1f),
            phaseRad = .2, radiusKm = 2439.7, semiMajorAxisAu = .3871, axialTiltDeg = .034
        )
        bodies += CelestialBody(
            "venus", "Venus", .53, 7.2, 224.701, -5832.5,
            floatArrayOf(.84f, .59f, .31f, 1f),
            phaseRad = 1.5, radiusKm = 6051.8, semiMajorAxisAu = .7233, axialTiltDeg = 177.36
        )
        bodies += CelestialBody(
            "earth", "Earth", .56, 9.5, 365.256, 23.9345,
            floatArrayOf(.18f, .44f, .86f, 1f),
            phaseRad = 2.5, radiusKm = 6371.0, semiMajorAxisAu = 1.0, axialTiltDeg = 23.44
        )
        bodies += CelestialBody(
            "moon", "Moon", .18, 1.15, 27.3217, 655.7,
            floatArrayOf(.69f, .69f, .67f, 1f),
            parentId = "earth", phaseRad = 1.1, radiusKm = 1737.4, axialTiltDeg = 6.68
        )
        bodies += CelestialBody(
            "mars", "Mars", .42, 12.3, 686.98, 24.6229,
            floatArrayOf(.72f, .24f, .10f, 1f),
            phaseRad = .8, radiusKm = 3389.5, semiMajorAxisAu = 1.5237, axialTiltDeg = 25.19
        )
        bodies += CelestialBody(
            "jupiter", "Jupiter", 1.22, 17.2, 4332.589, 9.925,
            floatArrayOf(.72f, .58f, .42f, 1f),
            phaseRad = 2.1, radiusKm = 69911.0, semiMajorAxisAu = 5.2029, axialTiltDeg = 3.13
        )
        bodies += CelestialBody(
            "saturn", "Saturn", 1.02, 22.5, 10759.22, 10.656,
            floatArrayOf(.79f, .68f, .43f, 1f),
            phaseRad = 2.7, radiusKm = 58232.0, semiMajorAxisAu = 9.5371, axialTiltDeg = 26.73
        )
        bodies += CelestialBody(
            "uranus", "Uranus", .72, 27.6, 30688.5, -17.24,
            floatArrayOf(.42f, .78f, .82f, 1f),
            phaseRad = 1.7, radiusKm = 25362.0, semiMajorAxisAu = 19.191, axialTiltDeg = 97.77
        )
        bodies += CelestialBody(
            "neptune", "Neptune", .70, 32.8, 60182.0, 16.11,
            floatArrayOf(.22f, .38f, .84f, 1f),
            phaseRad = .4, radiusKm = 24622.0, semiMajorAxisAu = 30.07, axialTiltDeg = 28.32
        )

        updateBodyPositions(0.0)
        cameraTarget = byId["earth"]?.position ?: Vec3d.ZERO
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.004f, 0.006f, 0.02f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        sphere = SphereMesh()
        planetProgram = createProgram(PLANET_VERTEX_SHADER, PLANET_FRAGMENT_SHADER)
        starProgram = createProgram(STAR_VERTEX_SHADER, STAR_FRAGMENT_SHADER)
        lineProgram = createProgram(LINE_VERTEX_SHADER, LINE_FRAGMENT_SHADER)
        ringProgram = createProgram(RING_VERTEX_SHADER, RING_FRAGMENT_SHADER)

        loadPlanetTextures()
        buildStars()
        buildOrbitBuffers()
        buildRingMesh()
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

        applyControlDamping(dt)

        val simSeconds = clock.advance(dt)
        updateBodyPositions(simSeconds)

        cloudRotation += dt * .012
        venusCloudRotation -= dt * .004

        updateCamera(dt)
        updateLabelSnapshots()

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        drawStars()
        if (showOrbits) drawOrbits()

        for (body in bodies) {
            drawBody(body)
            if (body.id == "earth" && earthCloudsTexture != 0) {
                drawOverlaySphere(body, earthCloudsTexture, 1.012f, cloudRotation.toFloat(), 1, .64f)
            }
            if (body.id == "venus" && venusAtmosphereTexture != 0) {
                drawOverlaySphere(body, venusAtmosphereTexture, 1.018f, venusCloudRotation.toFloat(), 2, .82f)
            }
        }

        drawSaturnRing()
    }

    @Synchronized
    fun orbitBy(dx: Float, dy: Float, viewportHeight: Int) {
        val h = max(1, viewportHeight)
        val speed = if (overview) rotateSpeedOverview else rotateSpeedFocused
        val radiansPerPixel = (2.0 * PI * speed) / h.toDouble()

        // Follow the finger direction. Overview is deliberately slower because
        // tiny distant targets need fine control.
        pendingYaw += dx.coerceIn(-120f, 120f) * radiansPerPixel
        pendingPitch += dy.coerceIn(-120f, 120f) * radiansPerPixel

        val yawLimit = if (overview) 0.95 else 1.25
        val pitchLimit = if (overview) 0.75 else 1.0
        pendingYaw = pendingYaw.coerceIn(-yawLimit, yawLimit)
        pendingPitch = pendingPitch.coerceIn(-pitchLimit, pitchLimit)
    }

    @Synchronized
    fun zoomBy(scaleFactor: Float) {
        if (!scaleFactor.isFinite() || scaleFactor <= 0f) return
        targetDistance = (targetDistance * scaleFactor).coerceIn(.35, 95.0)
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
    fun slower() {
        clock.slower()
    }

    @Synchronized
    fun faster() {
        clock.faster()
    }

    @Synchronized
    fun toggleOrbits(): Boolean {
        showOrbits = !showOrbits
        return showOrbits
    }

    fun labelSnapshots(): List<BodyLabelSnapshot> = latestLabels

    @Synchronized
    fun toggleOverview(): Boolean {
        pendingYaw = 0.0
        pendingPitch = 0.0

        if (!overview) {
            savedFocus = CameraState(selectedId, yaw, pitch, distance, targetDistance)
            overview = true
            selectedId = null

            // Fly outward instead of snapping. The stored focus state is kept
            // untouched so Return still goes back to the exact prior view.
            pendingYaw += (.72 - yaw)
            pendingPitch += (.38 - pitch)
            targetDistance = 43.0
            onSelectionChanged(null)
        } else {
            val state = savedFocus
            overview = false
            if (state != null) {
                selectedId = state.selectedId
                pendingYaw += (state.yaw - yaw)
                pendingPitch += (state.pitch - pitch)
                targetDistance = state.targetDistance
            } else {
                selectedId = "earth"
                targetDistance = 5.0
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

        // Preserve the current viewing direction and fly to the body.
        // This is much less disorienting than resetting yaw/pitch on every tap.
        targetDistance = max(body.radius * 7.5, 2.0)
        pendingYaw = 0.0
        pendingPitch = 0.0
        onSelectionChanged(id)
    }

    @Synchronized
    fun pick(screenX: Float, screenY: Float) {
        // Overview bodies are physically tiny on-screen. Give every visible body
        // a finger-sized screen-space target before falling back to true ray/sphere picking.
        val density = context.resources.displayMetrics.density
        val hitRadiusPx = (if (overview) 52f else 34f) * density
        val nearest = latestLabels
            .asSequence()
            .filter { it.visible }
            .map { label ->
                val dx = label.xPx - screenX
                val dy = label.yPx - screenY
                label to (dx * dx + dy * dy)
            }
            .filter { (_, d2) -> d2 <= hitRadiusPx * hitRadiusPx }
            .minByOrNull { (_, d2) -> d2 }

        if (nearest != null) {
            focus(nearest.first.id)
            return
        }

        val inv = FloatArray(16)
        if (!Matrix.invertM(inv, 0, viewProjection, 0)) return

        val x = (2f * screenX / width) - 1f
        val y = 1f - (2f * screenY / height)

        val near = unproject(inv, x, y, -1f)
        val far = unproject(inv, x, y, 1f)

        val origin = Vec3d(near[0].toDouble(), near[1].toDouble(), near[2].toDouble())
        val dir = (
            Vec3d(far[0].toDouble(), far[1].toDouble(), far[2].toDouble()) - origin
            ).normalized()

        var best: Pair<CelestialBody, Double>? = null

        for (body in bodies) {
            val oc = origin - body.position
            val b = oc.dot(dir)
            val c = oc.dot(oc) - body.radius * body.radius
            val discriminant = b * b - c
            if (discriminant < 0.0) continue

            val t = -b - sqrt(discriminant)
            if (t > 0.0 && (best == null || t < best!!.second)) {
                best = body to t
            }
        }

        best?.first?.let { focus(it.id) }
    }

    @Synchronized
    private fun applyControlDamping(dt: Double) {
        val frameScale = (dt * 60.0).coerceIn(0.0, 3.0)
        val apply = 1.0 - (1.0 - dampingFactor).pow(frameScale)

        if (kotlin.math.abs(pendingYaw) > 1e-7) {
            val step = pendingYaw * apply
            yaw += step
            pendingYaw -= step
        } else {
            pendingYaw = 0.0
        }

        if (kotlin.math.abs(pendingPitch) > 1e-7) {
            val step = pendingPitch * apply
            val next = (pitch + step).coerceIn(-1.42, 1.42)
            if (next == pitch && kotlin.math.abs(step) > 0.0) {
                pendingPitch = 0.0
            } else {
                pitch = next
                pendingPitch -= step
            }
        } else {
            pendingPitch = 0.0
        }

        // Critically-smoothed zoom/fly distance. This removes the abrupt
        // M3.2 "teleport" feel when focusing a distant planet.
        val zoomAlpha = 1.0 - exp(-9.0 * dt)
        distance += (targetDistance - distance) * zoomAlpha
    }

    @Synchronized
    private fun updateCamera(dt: Double) {
        val desiredTarget = selectedId?.let { byId[it]?.position } ?: Vec3d.ZERO
        val perFrameTargetDamping = if (selectedId != null) .16 else .11
        val targetAlpha =
            1.0 - (1.0 - perFrameTargetDamping).pow((dt * 60.0).coerceIn(0.0, 3.0))

        cameraTarget += (desiredTarget - cameraTarget) * targetAlpha

        val cp = cos(pitch)
        val desired = cameraTarget + Vec3d(
            cos(yaw) * cp * distance,
            sin(pitch) * distance,
            sin(yaw) * cp * distance
        )

        val motion = desired - previousCameraPosition
        val resolved = collision.resolveMotion(
            previousCameraPosition,
            motion,
            cameraRadius,
            skin = .025
        )

        cameraPosition = resolved.position

        selectedId?.let { id ->
            byId[id]?.let { body ->
                val radial = cameraPosition - body.position
                val minDistance = body.radius + cameraRadius + .04
                if (radial.length() < minDistance) {
                    val normal = if (radial.length() < 1e-8) {
                        Vec3d(0.0, 0.0, 1.0)
                    } else {
                        radial.normalized()
                    }
                    cameraPosition = body.position + normal * minDistance
                }
            }
        }

        previousCameraPosition = cameraPosition

        Matrix.setLookAtM(
            view, 0,
            cameraPosition.x.toFloat(),
            cameraPosition.y.toFloat(),
            cameraPosition.z.toFloat(),
            cameraTarget.x.toFloat(),
            cameraTarget.y.toFloat(),
            cameraTarget.z.toFloat(),
            0f, 1f, 0f
        )

        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)
    }

    private fun updateBodyPositions(simSeconds: Double) {
        val days = simSeconds / 86400.0
        val map = HashMap<String, CelestialBody>()

        for (body in bodies) {
            if (body.id == "sun") {
                body.position = Vec3d.ZERO
            } else {
                val angle = body.phaseRad + (2.0 * PI * days / body.orbitPeriodDays)
                val local = Vec3d(
                    cos(angle) * body.orbitRadius,
                    0.0,
                    sin(angle) * body.orbitRadius
                )
                body.position = body.parentId?.let { parent ->
                    (map[parent]?.position ?: Vec3d.ZERO) + local
                } ?: local
            }
            map[body.id] = body
        }

        collision.replaceSpheres(
            bodies.map { SphereCollider(it.id, it.position, it.radius) }
        )
    }

    private fun updateLabelSnapshots() {
        val out = ArrayList<BodyLabelSnapshot>(bodies.size)

        for (body in bodies) {
            val input = floatArrayOf(
                body.position.x.toFloat(),
                body.position.y.toFloat(),
                body.position.z.toFloat(),
                1f
            )
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
        buildBodyModel(body, 1f, 0f)

        GLES30.glUseProgram(planetProgram)
        bindPlanetCommon(body)

        val baseTexture = textures[body.id] ?: 0
        bindTexture(0, baseTexture)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(planetProgram, "uTexture"), 0)
        GLES30.glUniform1i(
            GLES30.glGetUniformLocation(planetProgram, "uUseTexture"),
            if (baseTexture != 0) 1 else 0
        )

        val night = if (body.id == "earth") earthNightTexture else 0
        bindTexture(1, night)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(planetProgram, "uNightTexture"), 1)
        GLES30.glUniform1i(
            GLES30.glGetUniformLocation(planetProgram, "uUseNight"),
            if (night != 0) 1 else 0
        )

        GLES30.glUniform1i(GLES30.glGetUniformLocation(planetProgram, "uMode"), 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(planetProgram, "uOpacity"), 1f)

        drawSphereGeometry()
    }

    private fun drawOverlaySphere(
        body: CelestialBody,
        texture: Int,
        radiusMultiplier: Float,
        extraRotation: Float,
        mode: Int,
        opacity: Float
    ) {
        GLES30.glDepthMask(false)

        buildBodyModel(body, radiusMultiplier, extraRotation)

        GLES30.glUseProgram(planetProgram)
        bindPlanetCommon(body)

        bindTexture(0, texture)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(planetProgram, "uTexture"), 0)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(planetProgram, "uUseTexture"), 1)

        bindTexture(1, 0)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(planetProgram, "uNightTexture"), 1)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(planetProgram, "uUseNight"), 0)

        GLES30.glUniform1i(GLES30.glGetUniformLocation(planetProgram, "uMode"), mode)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(planetProgram, "uOpacity"), opacity)

        drawSphereGeometry()

        GLES30.glDepthMask(true)
    }

    private fun buildBodyModel(body: CelestialBody, radiusMultiplier: Float, extraRotation: Float) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(
            model, 0,
            body.position.x.toFloat(),
            body.position.y.toFloat(),
            body.position.z.toFloat()
        )

        Matrix.rotateM(model, 0, body.axialTiltDeg.toFloat(), 0f, 0f, 1f)

        val j2000Millis = 946728000000L
        val elapsedHours = (clock.currentTimeMillis() - j2000Millis) / 3_600_000.0
        val baseRotation = if (body.rotationHours == 0.0) {
            0.0
        } else {
            (elapsedHours / body.rotationHours) * 360.0
        }

        Matrix.rotateM(
            model, 0,
            (baseRotation + Math.toDegrees(extraRotation.toDouble())).toFloat(),
            0f, 1f, 0f
        )

        val r = (body.radius * radiusMultiplier).toFloat()
        Matrix.scaleM(model, 0, r, r, r)

        Matrix.multiplyMM(mvp, 0, viewProjection, 0, model, 0)
    }

    private fun bindPlanetCommon(body: CelestialBody) {
        GLES30.glUniformMatrix4fv(
            GLES30.glGetUniformLocation(planetProgram, "uMvp"),
            1, false, mvp, 0
        )
        GLES30.glUniformMatrix4fv(
            GLES30.glGetUniformLocation(planetProgram, "uModel"),
            1, false, model, 0
        )
        GLES30.glUniform4fv(
            GLES30.glGetUniformLocation(planetProgram, "uColor"),
            1, body.color, 0
        )
        GLES30.glUniform3f(
            GLES30.glGetUniformLocation(planetProgram, "uCamera"),
            cameraPosition.x.toFloat(),
            cameraPosition.y.toFloat(),
            cameraPosition.z.toFloat()
        )
        GLES30.glUniform1f(
            GLES30.glGetUniformLocation(planetProgram, "uEmissive"),
            if (body.id == "sun") 1f else 0f
        )
    }

    private fun drawSphereGeometry() {
        val stride = 8 * 4

        sphere.vertices.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, sphere.vertices)

        sphere.vertices.position(3)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, sphere.vertices)

        sphere.vertices.position(6)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, stride, sphere.vertices)

        GLES30.glDrawElements(
            GLES30.GL_TRIANGLES,
            sphere.indexCount,
            GLES30.GL_UNSIGNED_SHORT,
            sphere.indices
        )

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glDisableVertexAttribArray(2)
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
        GLES30.glUniformMatrix4fv(
            GLES30.glGetUniformLocation(lineProgram, "uVp"),
            1, false, viewProjection, 0
        )
        GLES30.glLineWidth(1f)

        for (body in bodies) {
            if (body.id == "sun") continue

            val buffer = orbitBuffers[body.id] ?: continue
            val offset = body.parentId?.let { byId[it]?.position } ?: Vec3d.ZERO

            GLES30.glUniform3f(
                GLES30.glGetUniformLocation(lineProgram, "uOffset"),
                offset.x.toFloat(), offset.y.toFloat(), offset.z.toFloat()
            )
            GLES30.glUniform4f(
                GLES30.glGetUniformLocation(lineProgram, "uColor"),
                body.color[0] * .62f,
                body.color[1] * .62f,
                body.color[2] * .62f,
                .34f
            )

            buffer.position(0)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, buffer)
            GLES30.glDrawArrays(
                GLES30.GL_LINE_LOOP,
                0,
                orbitCounts[body.id] ?: 0
            )
            GLES30.glDisableVertexAttribArray(0)
        }
    }

    private fun buildRingMesh() {
        val segments = 256
        val inner = 1.28f
        val outer = 2.32f
        val values = FloatArray((segments + 1) * 2 * 5)
        var p = 0

        for (i in 0..segments) {
            val a = i.toDouble() / segments.toDouble() * PI * 2.0
            val c = cos(a).toFloat()
            val s = sin(a).toFloat()

            values[p++] = c * outer
            values[p++] = 0f
            values[p++] = s * outer
            values[p++] = 1f
            values[p++] = .5f

            values[p++] = c * inner
            values[p++] = 0f
            values[p++] = s * inner
            values[p++] = 0f
            values[p++] = .5f
        }

        ringBuffer = floatBuffer(values)
        ringVertexCount = (segments + 1) * 2
    }

    private fun drawSaturnRing() {
        val saturn = byId["saturn"] ?: return
        val buffer = ringBuffer ?: return

        if (saturnRingTexture == 0) return

        Matrix.setIdentityM(model, 0)
        Matrix.translateM(
            model, 0,
            saturn.position.x.toFloat(),
            saturn.position.y.toFloat(),
            saturn.position.z.toFloat()
        )
        Matrix.rotateM(
            model, 0,
            saturn.axialTiltDeg.toFloat(),
            0f, 0f, 1f
        )
        Matrix.scaleM(
            model, 0,
            saturn.radius.toFloat(),
            saturn.radius.toFloat(),
            saturn.radius.toFloat()
        )
        Matrix.multiplyMM(mvp, 0, viewProjection, 0, model, 0)

        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDepthMask(false)

        GLES30.glUseProgram(ringProgram)
        GLES30.glUniformMatrix4fv(
            GLES30.glGetUniformLocation(ringProgram, "uMvp"),
            1, false, mvp, 0
        )

        bindTexture(0, saturnRingTexture)
        GLES30.glUniform1i(
            GLES30.glGetUniformLocation(ringProgram, "uTexture"),
            0
        )

        buffer.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 20, buffer)

        buffer.position(3)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 20, buffer)

        GLES30.glDrawArrays(
            GLES30.GL_TRIANGLE_STRIP,
            0,
            ringVertexCount
        )

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)

        GLES30.glDepthMask(true)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
    }

    private fun buildStars() {
        val count = 6200
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

            values[i * 3] = (radius * q * cos(a)).toFloat()
            values[i * 3 + 1] = (z * radius).toFloat()
            values[i * 3 + 2] = (radius * q * sin(a)).toFloat()
        }

        starBuffer = floatBuffer(values)
        starCount = count
    }

    private fun drawStars() {
        GLES30.glDepthMask(false)
        GLES30.glUseProgram(starProgram)
        GLES30.glUniformMatrix4fv(
            GLES30.glGetUniformLocation(starProgram, "uVp"),
            1, false, viewProjection, 0
        )

        val buffer = starBuffer ?: return
        buffer.position(0)

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, buffer)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, starCount)
        GLES30.glDisableVertexAttribArray(0)

        GLES30.glDepthMask(true)
    }

    private fun loadPlanetTextures() {
        val ids = listOf(
            "sun",
            "mercury",
            "venus",
            "earth",
            "moon",
            "mars",
            "jupiter",
            "saturn",
            "uranus",
            "neptune"
        )

        for (id in ids) {
            val texture = loadTextureResource("planet_$id")
            if (texture != 0) textures[id] = texture
        }

        earthCloudsTexture = loadTextureResource("earth_clouds")
        earthNightTexture = loadTextureResource("earth_night")
        venusAtmosphereTexture = loadTextureResource("venus_atmosphere")
        saturnRingTexture = loadTextureResource("saturn_ring", repeatX = false)
    }

    private fun loadTextureResource(name: String, repeatX: Boolean = true): Int {
        val resId = context.resources.getIdentifier(
            name,
            "drawable",
            context.packageName
        )
        if (resId == 0) return 0

        val bitmap = BitmapFactory.decodeResource(
            context.resources,
            resId,
            BitmapFactory.Options().apply {
                inScaled = false
            }
        ) ?: return 0

        val handles = IntArray(1)
        GLES30.glGenTextures(1, handles, 0)
        val texture = handles[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_MIN_FILTER,
            GLES30.GL_LINEAR_MIPMAP_LINEAR
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_MAG_FILTER,
            GLES30.GL_LINEAR
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_WRAP_S,
            if (repeatX) GLES30.GL_REPEAT else GLES30.GL_CLAMP_TO_EDGE
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_WRAP_T,
            GLES30.GL_CLAMP_TO_EDGE
        )

        GLUtils.texImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            bitmap,
            0
        )
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        bitmap.recycle()

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return texture
    }

    private fun bindTexture(unit: Int, texture: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
    }

    private fun floatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                position(0)
            }

    private fun unproject(
        inv: FloatArray,
        x: Float,
        y: Float,
        z: Float
    ): FloatArray {
        val input = floatArrayOf(x, y, z, 1f)
        val output = FloatArray(4)

        Matrix.multiplyMV(output, 0, inv, 0, input, 0)

        val w = output[3].takeIf {
            kotlin.math.abs(it) > 1e-7f
        } ?: 1f

        return floatArrayOf(
            output[0] / w,
            output[1] / w,
            output[2] / w
        )
    }

    private fun createProgram(vs: String, fs: String): Int {
        fun shader(type: Int, source: String): Int {
            val id = GLES30.glCreateShader(type)
            GLES30.glShaderSource(id, source)
            GLES30.glCompileShader(id)

            val status = IntArray(1)
            GLES30.glGetShaderiv(
                id,
                GLES30.GL_COMPILE_STATUS,
                status,
                0
            )

            if (status[0] == 0) {
                error(GLES30.glGetShaderInfoLog(id))
            }

            return id
        }

        val vertex = shader(
            GLES30.GL_VERTEX_SHADER,
            vs
        )
        val fragment = shader(
            GLES30.GL_FRAGMENT_SHADER,
            fs
        )

        return GLES30.glCreateProgram().also { program ->
            GLES30.glAttachShader(program, vertex)
            GLES30.glAttachShader(program, fragment)
            GLES30.glLinkProgram(program)

            val status = IntArray(1)
            GLES30.glGetProgramiv(
                program,
                GLES30.GL_LINK_STATUS,
                status,
                0
            )

            if (status[0] == 0) {
                error(GLES30.glGetProgramInfoLog(program))
            }

            GLES30.glDeleteShader(vertex)
            GLES30.glDeleteShader(fragment)
        }
    }

    companion object {
        private const val PLANET_VERTEX_SHADER = """#version 300 es
layout(location=0) in vec3 aPosition;
layout(location=1) in vec3 aNormal;
layout(location=2) in vec2 aUv;

uniform mat4 uMvp;
uniform mat4 uModel;

out vec3 vNormal;
out vec3 vWorld;
out vec2 vUv;

void main() {
    vec4 world = uModel * vec4(aPosition, 1.0);
    vWorld = world.xyz;
    vNormal = normalize(mat3(uModel) * aNormal);
    vUv = aUv;
    gl_Position = uMvp * vec4(aPosition, 1.0);
}
"""

        private const val PLANET_FRAGMENT_SHADER = """#version 300 es
precision highp float;

in vec3 vNormal;
in vec3 vWorld;
in vec2 vUv;

uniform vec4 uColor;
uniform vec3 uCamera;
uniform float uEmissive;
uniform sampler2D uTexture;
uniform sampler2D uNightTexture;
uniform int uUseTexture;
uniform int uUseNight;
uniform int uMode;
uniform float uOpacity;

out vec4 fragColor;

void main() {
    vec4 texel = uUseTexture == 1
        ? texture(uTexture, vUv)
        : uColor;

    if (uMode == 1) {
        float cloud = dot(texel.rgb, vec3(0.333333));
        if (cloud < 0.025) discard;
        fragColor = vec4(texel.rgb, cloud * uOpacity);
        return;
    }

    if (uMode == 2) {
        fragColor = vec4(texel.rgb, uOpacity);
        return;
    }

    vec3 N = normalize(vNormal);
    vec3 L = normalize(-vWorld);
    vec3 V = normalize(uCamera - vWorld);

    float ndl = max(dot(N, L), 0.0);
    float rim = pow(1.0 - max(dot(N, V), 0.0), 3.0);
    float light = mix(0.075 + 0.925 * ndl, 1.0, uEmissive);

    vec3 color = texel.rgb * light;

    if (uUseNight == 1 && uEmissive < 0.5) {
        vec3 night = texture(uNightTexture, vUv).rgb;
        float darkness = pow(1.0 - ndl, 1.7);
        color += night * darkness * 0.58;
    }

    color += texel.rgb * rim * 0.08 * (1.0 - uEmissive);
    fragColor = vec4(color, texel.a * uColor.a);
}
"""

        private const val STAR_VERTEX_SHADER = """#version 300 es
layout(location=0) in vec3 aPosition;
uniform mat4 uVp;

void main() {
    gl_Position = uVp * vec4(aPosition, 1.0);
    gl_PointSize = 2.0;
}
"""

        private const val STAR_FRAGMENT_SHADER = """#version 300 es
precision mediump float;

out vec4 fragColor;

void main() {
    vec2 p = gl_PointCoord - vec2(0.5);
    float d = dot(p, p);
    if (d > 0.25) discard;
    float a = 1.0 - smoothstep(0.08, 0.25, d);
    fragColor = vec4(0.82, 0.88, 1.0, 0.72 * a);
}
"""

        private const val LINE_VERTEX_SHADER = """#version 300 es
layout(location=0) in vec3 aPosition;
uniform mat4 uVp;
uniform vec3 uOffset;

void main() {
    gl_Position = uVp * vec4(aPosition + uOffset, 1.0);
}
"""

        private const val LINE_FRAGMENT_SHADER = """#version 300 es
precision mediump float;

uniform vec4 uColor;
out vec4 fragColor;

void main() {
    fragColor = uColor;
}
"""

        private const val RING_VERTEX_SHADER = """#version 300 es
layout(location=0) in vec3 aPosition;
layout(location=1) in vec2 aUv;
uniform mat4 uMvp;
out vec2 vUv;

void main() {
    vUv = aUv;
    gl_Position = uMvp * vec4(aPosition, 1.0);
}
"""

        private const val RING_FRAGMENT_SHADER = """#version 300 es
precision mediump float;

in vec2 vUv;
uniform sampler2D uTexture;
out vec4 fragColor;

void main() {
    vec4 t = texture(uTexture, vUv);
    float band = max(t.r, max(t.g, t.b));
    float alpha = t.a * band * 0.92;
    if (alpha < 0.02) discard;
    fragColor = vec4(t.rgb, alpha);
}
"""
    }
}
