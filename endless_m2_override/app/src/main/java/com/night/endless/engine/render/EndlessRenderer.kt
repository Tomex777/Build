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
import com.night.endless.engine.scene.AsteroidBeltModel
import com.night.endless.engine.scene.CelestialBody
import com.night.endless.engine.scene.CosmicPointClouds
import com.night.endless.engine.scene.DeepTimeHistory
import com.night.endless.engine.scene.ExplorationScale
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

data class ApproachSnapshot(
    val bodyId: String?,
    val altitudeKm: Double,
    val stage: String,
    val closeLod: Boolean
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
    private var marsCloseTexture = 0
    private var marsNormalTexture = 0
    private var moonCloseTexture = 0
    private var moonNormalTexture = 0

    private var starBuffer: FloatBuffer? = null
    private var starCount = 0
    private val asteroidBeltOrbits = AsteroidBeltModel.build()
    private var asteroidBeltBuffer: FloatBuffer? = null
    private var localStarsBuffer: FloatBuffer? = null
    private var localStarsCount = 0
    private var milkyWayBuffer: FloatBuffer? = null
    private var milkyWayCount = 0
    private var observableUniverseBuffer: FloatBuffer? = null
    private var observableUniverseCount = 0
    private var cosmicOriginBuffer: FloatBuffer? = null
    private val orbitBuffers = mutableMapOf<String, FloatBuffer>()
    private val orbitCounts = mutableMapOf<String, Int>()
    private val formationDiskBuffers = mutableListOf<FloatBuffer>()
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
    @Volatile private var explorationScale = ExplorationScale.SOLAR_SYSTEM
    private var cosmicReturnFocus: CameraState? = null

    private var cameraPosition = Vec3d(0.0, 8.0, 38.0)
    private var previousCameraPosition = cameraPosition
    private var cameraTarget = Vec3d.ZERO

    @Volatile
    private var latestApproach = ApproachSnapshot(null, Double.POSITIVE_INFINITY, "SPACE", false)

    private var cloudRotation = 0.0
    private var venusCloudRotation = 0.0
    private var marsSurfaceMode = false
    private var marsSurfaceX = 0.0
    private var marsSurfaceZ = 0.0
    private var moonSurfaceMode = false
    private var moonSurfaceX = 0.0
    private var moonSurfaceZ = 0.0
    @Volatile private var deepTimeAgeGa = 0.0
    private var marsSurfaceTerrain: MarsSurfaceTerrain? = null
    private var moonSurfaceTerrain: MoonSurfaceTerrain? = null

    @Volatile
    private var latestLabels: List<BodyLabelSnapshot> = emptyList()

    @Volatile
    private var completedFrames = 0L

    data class CameraState(
        val selectedId: String?,
        val yaw: Double,
        val pitch: Double,
        val distance: Double,
        val targetDistance: Double
    )

    data class ExplorationState(
        val selectedId: String?,
        val overview: Boolean,
        val showOrbits: Boolean,
        val yaw: Double,
        val pitch: Double,
        val distance: Double,
        val targetDistance: Double,
        val savedFocus: CameraState?,
        val marsSurfaceMode: Boolean,
        val marsSurfaceX: Double,
        val marsSurfaceZ: Double,
        val moonSurfaceMode: Boolean,
        val moonSurfaceX: Double,
        val moonSurfaceZ: Double,
        val clockState: UniverseClock.State,
        val explorationScale: ExplorationScale = ExplorationScale.SOLAR_SYSTEM,
        val cosmicReturnFocus: CameraState? = null
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
            "ceres", "Ceres", .18, 14.55, 1680.0, 9.074,
            floatArrayOf(.50f, .49f, .47f, 1f),
            phaseRad = 1.35, radiusKm = 473.0, semiMajorAxisAu = 2.77, axialTiltDeg = 4.0
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

        sphere = SphereMesh(stacks = 96, slices = 144)
        planetProgram = createProgram(PLANET_VERTEX_SHADER, PLANET_FRAGMENT_SHADER)
        starProgram = createProgram(STAR_VERTEX_SHADER, STAR_FRAGMENT_SHADER)
        lineProgram = createProgram(LINE_VERTEX_SHADER, LINE_FRAGMENT_SHADER)
        ringProgram = createProgram(RING_VERTEX_SHADER, RING_FRAGMENT_SHADER)

        loadPlanetTextures()
        buildStars()
        asteroidBeltBuffer = floatBuffer(AsteroidBeltModel.positions(asteroidBeltOrbits, 0.0))
        val localStars = CosmicPointClouds.localStars()
        localStarsBuffer = floatBuffer(localStars)
        localStarsCount = localStars.size / 3
        val milkyWay = CosmicPointClouds.milkyWay()
        milkyWayBuffer = floatBuffer(milkyWay)
        milkyWayCount = milkyWay.size / 3
        val universe = CosmicPointClouds.observableUniverse()
        observableUniverseBuffer = floatBuffer(universe)
        observableUniverseCount = universe.size / 3
        cosmicOriginBuffer = floatBuffer(floatArrayOf(0f, 0f, 0f))
        buildOrbitBuffers()
        buildFormationDiskBuffers()
        buildRingMesh()
        marsSurfaceTerrain = MarsSurfaceTerrain()
        moonSurfaceTerrain = MoonSurfaceTerrain()
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

        if (marsSurfaceMode) {
            drawMarsSurfaceFrame()
            completedFrames++
            return
        }
        if (moonSurfaceMode) {
            drawMoonSurfaceFrame()
            completedFrames++
            return
        }
        if (explorationScale != ExplorationScale.SOLAR_SYSTEM) {
            updateProjectionForApproach()
            updateCamera(dt)
            latestApproach = ApproachSnapshot(null, Double.POSITIVE_INFINITY, "SPACE", false)
            latestLabels = emptyList()
            GLES30.glClearColor(0.004f, 0.006f, 0.02f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
            drawCosmicScale()
            completedFrames++
            return
        }
        updateProjectionForApproach()
        updateCamera(dt)
        updateApproachSnapshot()
        updateLabelSnapshots()
        GLES30.glClearColor(0.004f, 0.006f, 0.02f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        drawStars()
        drawFormationDisk()
        drawAsteroidBelt(clock.seconds())
        if (showOrbits) drawOrbits()

        for (body in bodies) {
            drawBody(body)
            if (body.id == "earth" && earthCloudsTexture != 0 && deepTimeAgeGa <= 0.0001) {
                drawOverlaySphere(body, earthCloudsTexture, 1.012f, cloudRotation.toFloat(), 1, .64f)
            }
            if (body.id == "venus" && venusAtmosphereTexture != 0) {
                drawOverlaySphere(body, venusAtmosphereTexture, 1.018f, venusCloudRotation.toFloat(), 2, .82f)
            }
            if (body.id == "mars") {
                drawMarsAtmosphere(body)
            }
        }

        drawSaturnRing()
        completedFrames++
    }

    fun completedFrameCount(): Long = completedFrames

    /** Deep time is a separate master history clock; it never changes orbital days. */
    fun setDeepTimeAgeGa(ageGa: Double) {
        deepTimeAgeGa = ageGa.takeIf { it.isFinite() }?.coerceIn(-7.0, DeepTimeHistory.OLDEST_AGE_GA) ?: 0.0
    }

    fun deepTimeAgeGa(): Double = deepTimeAgeGa

    @Synchronized
    fun snapshotState(): ExplorationState = ExplorationState(
        selectedId = selectedId,
        overview = overview,
        showOrbits = showOrbits,
        yaw = yaw,
        pitch = pitch,
        distance = distance,
        targetDistance = targetDistance,
        savedFocus = savedFocus,
        marsSurfaceMode = marsSurfaceMode,
        marsSurfaceX = marsSurfaceX,
        marsSurfaceZ = marsSurfaceZ,
        moonSurfaceMode = moonSurfaceMode,
        moonSurfaceX = moonSurfaceX,
        moonSurfaceZ = moonSurfaceZ,
        clockState = clock.snapshot(),
        explorationScale = explorationScale,
        cosmicReturnFocus = cosmicReturnFocus
    )

    @Synchronized
    fun restoreState(state: ExplorationState) {
        clock.restore(state.clockState)
        updateBodyPositions(clock.seconds())

        val restoreSurfaceBody = when {
            state.moonSurfaceMode -> "moon"
            state.marsSurfaceMode -> "mars"
            else -> null
        }
        overview = if (restoreSurfaceBody != null) false else state.overview
        selectedId = when {
            restoreSurfaceBody != null -> restoreSurfaceBody
            overview -> null
            state.selectedId != null && byId.containsKey(state.selectedId) -> state.selectedId
            else -> "earth"
        }
        showOrbits = state.showOrbits

        yaw = state.yaw.takeIf { it.isFinite() } ?: 0.72
        pitch = (state.pitch.takeIf { it.isFinite() } ?: 0.28).coerceIn(-1.42, 1.42)

        val body = selectedId?.let { byId[it] }
        val minimumDistance = if (body != null) body.radius * 1.003 else 0.35
        distance = (state.distance.takeIf { it.isFinite() } ?: 5.0)
            .coerceIn(minimumDistance, 95.0)
        targetDistance = (state.targetDistance.takeIf { it.isFinite() } ?: distance)
            .coerceIn(minimumDistance, 95.0)

        savedFocus = state.savedFocus?.let { focus ->
            CameraState(
                selectedId = focus.selectedId?.takeIf { byId.containsKey(it) },
                yaw = focus.yaw.takeIf { it.isFinite() } ?: yaw,
                pitch = (focus.pitch.takeIf { it.isFinite() } ?: pitch).coerceIn(-1.42, 1.42),
                distance = (focus.distance.takeIf { it.isFinite() } ?: distance).coerceIn(0.35, 95.0),
                targetDistance = (focus.targetDistance.takeIf { it.isFinite() } ?: targetDistance)
                    .coerceIn(0.35, 95.0)
            )
        }

        marsSurfaceMode = restoreSurfaceBody == "mars"
        marsSurfaceX = (state.marsSurfaceX.takeIf { it.isFinite() } ?: 0.0).coerceIn(-8.0, 8.0)
        marsSurfaceZ = (state.marsSurfaceZ.takeIf { it.isFinite() } ?: 0.0).coerceIn(-8.0, 8.0)
        moonSurfaceMode = restoreSurfaceBody == "moon"
        moonSurfaceX = (state.moonSurfaceX.takeIf { it.isFinite() } ?: 0.0).coerceIn(-8.0, 8.0)
        moonSurfaceZ = (state.moonSurfaceZ.takeIf { it.isFinite() } ?: 0.0).coerceIn(-8.0, 8.0)
        pendingYaw = 0.0
        pendingPitch = 0.0

        cameraTarget = selectedId?.let { byId[it]?.position } ?: Vec3d.ZERO
        val cp = cos(pitch)
        cameraPosition = cameraTarget + Vec3d(
            cos(yaw) * cp * distance,
            sin(pitch) * distance,
            sin(yaw) * cp * distance
        )
        previousCameraPosition = cameraPosition
        lastNanos = System.nanoTime()
        latestLabels = emptyList()
        latestApproach = if (restoreSurfaceBody != null) {
            ApproachSnapshot(restoreSurfaceBody, 0.0, "SURFACE SKIM", true)
        } else {
            updateApproachSnapshot()
            latestApproach
        }
        onSelectionChanged(selectedId)
    }

    @Synchronized
    fun orbitBy(dx: Float, dy: Float, viewportHeight: Int) {
        val h = max(1, viewportHeight)
        if (marsSurfaceMode || moonSurfaceMode) {
            yaw += dx.coerceIn(-120f, 120f) * (2.0 * PI / h.toDouble()) * 0.22
            pitch = (pitch - dy.coerceIn(-120f, 120f) * (2.0 * PI / h.toDouble()) * 0.22).coerceIn(-0.42, 0.42)
            return
        }
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
        if (marsSurfaceMode || moonSurfaceMode || !scaleFactor.isFinite() || scaleFactor <= 0f) return
        val body = selectedId?.let { byId[it] }
        val minimum = if (body != null) body.radius * 1.003 else .35
        targetDistance = (targetDistance * scaleFactor).coerceIn(minimum, 95.0)
    }

    fun approachSnapshot(): ApproachSnapshot = latestApproach

    @Synchronized
    fun approachSelected() {
        val body = selectedId?.let { byId[it] } ?: return
        overview = false
        targetDistance = when (body.id) {
            "mars" -> body.radius * 1.018
            "moon" -> body.radius * 1.18
            "ceres" -> body.radius * 1.90
            "sun" -> body.radius * 2.80
            "saturn" -> body.radius * 3.10
            "jupiter" -> body.radius * 2.15
            "uranus", "neptune" -> body.radius * 2.25
            else -> body.radius * 2.05
        }
    }

    @Synchronized
    fun descendSelected() {
        val body = selectedId?.let { byId[it] } ?: return
        overview = false
        targetDistance = when (body.id) {
            "mars" -> body.radius * 1.003
            "moon" -> if (latestApproach.stage == "LOW ORBIT") {
                body.radius * 1.003
            } else {
                body.radius * 1.035
            }
            else -> return
        }
    }

    @Synchronized
    fun pullBackSelected() {
        val body = selectedId?.let { byId[it] } ?: return
        targetDistance = max(body.radius * 7.5, 2.0)
    }

    @Synchronized
    fun isSurfaceMode(): Boolean = marsSurfaceMode || moonSurfaceMode

    @Synchronized
    fun surfaceBodyId(): String? = when {
        marsSurfaceMode -> "mars"
        moonSurfaceMode -> "moon"
        else -> null
    }

    @Synchronized
    fun landOnMars(): Boolean {
        if (selectedId != "mars" || latestApproach.stage != "SURFACE SKIM") return false
        moonSurfaceMode = false
        marsSurfaceX = 0.0
        marsSurfaceZ = 0.0
        marsSurfaceMode = true
        pitch = -0.04
        return true
    }

    @Synchronized
    fun landOnMoon(): Boolean {
        if (selectedId != "moon" || latestApproach.stage != "SURFACE SKIM") return false
        marsSurfaceMode = false
        moonSurfaceX = 1.5
        moonSurfaceZ = -0.5
        moonSurfaceMode = true
        // The first landed frame should show nearby relief without snapping the
        // user's heading away from the approach direction.
        pitch = -0.09
        return true
    }

    @Synchronized
    fun walkSurface(forward: Float, strafe: Float) {
        if (!marsSurfaceMode && !moonSurfaceMode) return
        val magnitude = sqrt(forward * forward + strafe * strafe).coerceAtLeast(1f)
        val f = forward / magnitude
        val s = strafe / magnitude
        val sinHeading = sin(yaw)
        val cosHeading = cos(yaw)

        if (marsSurfaceMode) {
            val step = 0.20
            val nextX = (marsSurfaceX + (sinHeading * f + cosHeading * s) * step).coerceIn(-8.0, 8.0)
            val nextZ = (marsSurfaceZ + (-cosHeading * f + sinHeading * s) * step).coerceIn(-8.0, 8.0)
            val rise = MarsSurfaceTerrain.heightAt(nextX, nextZ) -
                MarsSurfaceTerrain.heightAt(marsSurfaceX, marsSurfaceZ)
            if (rise <= 0.055 && rise >= -0.08) {
                marsSurfaceX = nextX
                marsSurfaceZ = nextZ
            }
            return
        }

        val step = 0.27
        val nextX = (moonSurfaceX + (sinHeading * f + cosHeading * s) * step).coerceIn(-8.0, 8.0)
        val nextZ = (moonSurfaceZ + (-cosHeading * f + sinHeading * s) * step).coerceIn(-8.0, 8.0)
        val rise = MoonSurfaceTerrain.heightAt(nextX, nextZ) -
            MoonSurfaceTerrain.heightAt(moonSurfaceX, moonSurfaceZ)
        if (rise <= 0.075 && rise >= -0.10) {
            moonSurfaceX = nextX
            moonSurfaceZ = nextZ
        }
    }

    @Synchronized
    fun surfaceCoordinates(): Pair<Double, Double> = when {
        marsSurfaceMode -> marsSurfaceX to marsSurfaceZ
        moonSurfaceMode -> moonSurfaceX to moonSurfaceZ
        else -> 0.0 to 0.0
    }

    @Synchronized
    fun surfaceOrientation(): Pair<Double, Double> = yaw to pitch

    @Synchronized
    fun takeOffMars(): Boolean {
        if (!marsSurfaceMode) return false
        marsSurfaceMode = false
        val mars = byId["mars"] ?: return false
        targetDistance = mars.radius * 1.08
        previousCameraPosition = mars.position + Vec3d(0.0, 0.0, mars.radius * 1.08)
        cameraPosition = previousCameraPosition
        return true
    }

    @Synchronized
    fun takeOffMoon(): Boolean {
        if (!moonSurfaceMode) return false
        moonSurfaceMode = false
        val moon = byId["moon"] ?: return false
        targetDistance = moon.radius * 1.12
        previousCameraPosition = moon.position + Vec3d(0.0, 0.0, moon.radius * 1.12)
        cameraPosition = previousCameraPosition
        return true
    }

    @Synchronized
    fun takeOffSurface(): Boolean = when {
        marsSurfaceMode -> takeOffMars()
        moonSurfaceMode -> takeOffMoon()
        else -> false
    }

    private fun drawMarsSurfaceFrame() {
        GLES30.glClearColor(0.055f, 0.027f, 0.018f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        Matrix.perspectiveM(projection, 0, 70f, width.toFloat() / height, 0.04f, 40f)
        val eyeY = MarsSurfaceTerrain.heightAt(marsSurfaceX, marsSurfaceZ) + 0.22
        val lookX = sin(yaw) * cos(pitch)
        val lookY = sin(pitch)
        val lookZ = -cos(yaw) * cos(pitch)
        Matrix.setLookAtM(
            view, 0,
            0f, eyeY.toFloat(), 0f,
            lookX.toFloat(), (eyeY + lookY).toFloat(), lookZ.toFloat(),
            0f, 1f, 0f
        )
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)
        marsSurfaceTerrain?.draw(viewProjection, -marsSurfaceX.toFloat(), -marsSurfaceZ.toFloat())
    }

    private fun drawMoonSurfaceFrame() {
        GLES30.glClearColor(0.0015f, 0.0015f, 0.0025f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        Matrix.perspectiveM(projection, 0, 68f, width.toFloat() / height, 0.035f, 180f)
        val eyeY = MoonSurfaceTerrain.heightAt(moonSurfaceX, moonSurfaceZ) + 0.19
        val lookX = sin(yaw) * cos(pitch)
        val lookY = sin(pitch)
        val lookZ = -cos(yaw) * cos(pitch)
        Matrix.setLookAtM(
            view, 0,
            0f, eyeY.toFloat(), 0f,
            lookX.toFloat(), (eyeY + lookY).toFloat(), lookZ.toFloat(),
            0f, 1f, 0f
        )
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)
        drawStars()
        moonSurfaceTerrain?.draw(viewProjection, -moonSurfaceX.toFloat(), -moonSurfaceZ.toFloat())
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

        val focusedBody = selectedId?.let { byId[it] }
        val closeToBody = focusedBody != null && distance < focusedBody.radius * 2.5
        val cameraRadius = if (closeToBody) {
            max(focusedBody!!.radius * 0.0008, 0.00025)
        } else {
            0.02
        }
        val skin = if (closeToBody) max(focusedBody!!.radius * 0.00035, 0.00012) else .004

        val motion = desired - previousCameraPosition
        val resolved = collision.resolveMotion(
            previousCameraPosition,
            motion,
            cameraRadius,
            skin = skin
        )

        cameraPosition = resolved.position

        selectedId?.let { id ->
            byId[id]?.let { body ->
                val radial = cameraPosition - body.position
                val minDistance = body.radius + cameraRadius + skin
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
            bodies.filter { isBodyFormed(it.id, deepTimeAgeGa) }
                .map { SphereCollider(it.id, it.position, it.radius) }
        )
    }

    private fun updateLabelSnapshots() {
        val out = ArrayList<BodyLabelSnapshot>(bodies.size)

        for (body in bodies) {
            if (!isBodyFormed(body.id, deepTimeAgeGa)) {
                out += BodyLabelSnapshot(body.id, body.name, 0f, 0f, false)
                continue
            }
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
            val closeSurface = latestApproach.bodyId == selectedId &&
                (
                    latestApproach.stage == "ATMOSPHERE" ||
                        latestApproach.stage == "LOW ORBIT" ||
                        latestApproach.stage == "SURFACE SKIM"
                    )
            val visible = !closeSurface && nx in -1.15f..1.15f && ny in -1.15f..1.15f
            val sx = (nx * .5f + .5f) * width
            val sy = (1f - (ny * .5f + .5f)) * height

            out += BodyLabelSnapshot(body.id, body.name, sx, sy, visible)
        }

        latestLabels = out
    }

    private fun drawBody(body: CelestialBody) {
        if (!isBodyFormed(body.id, deepTimeAgeGa)) return
        val sunExpansion = if (body.id == "sun") when {
            deepTimeAgeGa <= -6.0 -> .30f
            deepTimeAgeGa < 0.0 -> (1.0 + min(1.2, -deepTimeAgeGa * .24)).toFloat()
            else -> 1f
        } else 1f
        buildBodyModel(body, sunExpansion, 0f)

        GLES30.glUseProgram(planetProgram)
        bindPlanetCommon(body)

        val useMarsClose = body.id == "mars" &&
            latestApproach.bodyId == "mars" &&
            latestApproach.closeLod &&
            marsCloseTexture != 0
        val useMoonClose = body.id == "moon" &&
            latestApproach.bodyId == "moon" &&
            latestApproach.closeLod &&
            moonCloseTexture != 0
        val baseTexture = when {
            useMarsClose -> marsCloseTexture
            useMoonClose -> moonCloseTexture
            else -> textures[body.id] ?: 0
        }
        bindTexture(0, baseTexture)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(planetProgram, "uTexture"), 0)
        GLES30.glUniform1i(
            GLES30.glGetUniformLocation(planetProgram, "uUseTexture"),
            if (baseTexture != 0) 1 else 0
        )

        val night = if (body.id == "earth" && deepTimeAgeGa <= 0.0001) earthNightTexture else 0
        bindTexture(1, night)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(planetProgram, "uNightTexture"), 1)
        GLES30.glUniform1i(
            GLES30.glGetUniformLocation(planetProgram, "uUseNight"),
            if (night != 0) 1 else 0
        )

        val normal = when {
            useMarsClose -> marsNormalTexture
            useMoonClose -> moonNormalTexture
            else -> 0
        }
        bindTexture(2, normal)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(planetProgram, "uNormalTexture"), 2)
        GLES30.glUniform1i(
            GLES30.glGetUniformLocation(planetProgram, "uUseNormal"),
            if (normal != 0) 1 else 0
        )
        GLES30.glUniform1i(
            GLES30.glGetUniformLocation(planetProgram, "uCloseMaterial"),
            when {
                useMarsClose -> 1
                useMoonClose -> 2
                else -> 0
            }
        )

        GLES30.glUniform1i(GLES30.glGetUniformLocation(planetProgram, "uMode"), 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(planetProgram, "uOpacity"), 1f)
        val historyState = when (body.id) {
            "earth" -> DeepTimeHistory.earthVisualState(deepTimeAgeGa)
            "mars" -> DeepTimeHistory.marsVisualState(deepTimeAgeGa)
            "moon" -> DeepTimeHistory.moonVisualState(deepTimeAgeGa)
            else -> null
        }
        GLES30.glUniform1f(GLES30.glGetUniformLocation(planetProgram, "uHistoryLava"), historyState?.lava ?: 0f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(planetProgram, "uHistoryIce"), historyState?.ice ?: 0f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(planetProgram, "uHistoryOcean"), historyState?.ocean ?: 1f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(planetProgram, "uHistoryAtmosphere"), historyState?.atmosphere ?: 1f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(planetProgram, "uHistoryImpact"), historyState?.impact ?: 0f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(planetProgram, "uHistoryWater"), historyState?.water ?: 0f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(planetProgram, "uHistoryBasalt"), historyState?.basalt ?: 0f)
        GLES30.glUniform1f(
            GLES30.glGetUniformLocation(planetProgram, "uHistoryFuture"),
            if (body.id == "sun" && deepTimeAgeGa < 0.0 && deepTimeAgeGa > -6.0) (-deepTimeAgeGa / 5.0).toFloat().coerceIn(0f, 1f) else 0f
        )

        drawSphereGeometry()
        GLES30.glUniform1f(GLES30.glGetUniformLocation(planetProgram, "uHistoryLava"), 0f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(planetProgram, "uHistoryIce"), 0f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(planetProgram, "uHistoryOcean"), 1f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(planetProgram, "uHistoryAtmosphere"), 1f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(planetProgram, "uHistoryImpact"), 0f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(planetProgram, "uHistoryWater"), 0f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(planetProgram, "uHistoryBasalt"), 0f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(planetProgram, "uHistoryFuture"), 0f)
    }

    private fun isBodyFormed(id: String, ageGa: Double): Boolean {
        if (ageGa <= 0.0) return true
        val formationAgeGa = when (id) {
            "sun" -> 4.57
            "moon" -> 4.47
            "mars" -> 4.50
            "ceres" -> 4.50
            "jupiter", "saturn" -> 4.55
            "uranus", "neptune" -> 4.53
            else -> 4.54
        }
        return ageGa <= formationAgeGa
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
        bindTexture(2, 0)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(planetProgram, "uNormalTexture"), 2)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(planetProgram, "uUseNormal"), 0)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(planetProgram, "uCloseMaterial"), 0)

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
        GLES30.glUniform1f(GLES30.glGetUniformLocation(starProgram, "uPointSize"), 2.0f)
        GLES30.glUniform4f(GLES30.glGetUniformLocation(starProgram, "uColor"), 0.82f, 0.88f, 1.0f, 0.72f)

        val buffer = starBuffer ?: return
        buffer.position(0)

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, buffer)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, starCount)
        GLES30.glDisableVertexAttribArray(0)

        GLES30.glDepthMask(true)
    }

    private fun drawAsteroidBelt(simulationSeconds: Double) {
        if (deepTimeAgeGa > 4.50) return
        val buffer = asteroidBeltBuffer ?: return
        val positions = AsteroidBeltModel.positions(asteroidBeltOrbits, simulationSeconds)
        buffer.clear()
        buffer.put(positions)
        buffer.position(0)

        GLES30.glDepthMask(false)
        GLES30.glUseProgram(starProgram)
        GLES30.glUniformMatrix4fv(
            GLES30.glGetUniformLocation(starProgram, "uVp"),
            1, false, viewProjection, 0
        )
        GLES30.glUniform1f(GLES30.glGetUniformLocation(starProgram, "uPointSize"), if (selectedId == "ceres") 3.4f else 2.35f)
        GLES30.glUniform4f(GLES30.glGetUniformLocation(starProgram, "uColor"), 0.67f, 0.62f, 0.54f, 0.62f)

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, buffer)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, asteroidBeltOrbits.size)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDepthMask(true)
    }

    private fun drawCosmicScale() {
        when (explorationScale) {
            ExplorationScale.SOLAR_SYSTEM -> Unit
            ExplorationScale.LOCAL_STARS -> {
                drawStars()
                drawPointCloud(localStarsBuffer, localStarsCount, 3.0f, floatArrayOf(.80f, .88f, 1.0f, .78f))
                drawPointCloud(cosmicOriginBuffer, 1, 7.0f, floatArrayOf(1.0f, .72f, .28f, 1.0f))
            }
            ExplorationScale.MILKY_WAY -> {
                drawPointCloud(milkyWayBuffer, milkyWayCount, 2.0f, floatArrayOf(.88f, .84f, .78f, .55f))
                drawPointCloud(cosmicOriginBuffer, 1, 5.5f, floatArrayOf(.55f, .78f, 1.0f, 1.0f))
            }
            ExplorationScale.OBSERVABLE_UNIVERSE -> {
                drawPointCloud(
                    observableUniverseBuffer,
                    observableUniverseCount,
                    2.2f,
                    floatArrayOf(.66f, .78f, 1.0f, .58f)
                )
                drawPointCloud(cosmicOriginBuffer, 1, 5.0f, floatArrayOf(.45f, .72f, 1.0f, 1.0f))
            }
        }
    }

    private fun drawPointCloud(
        buffer: FloatBuffer?,
        count: Int,
        pointSize: Float,
        color: FloatArray
    ) {
        if (buffer == null || count <= 0) return
        GLES30.glDepthMask(false)
        GLES30.glUseProgram(starProgram)
        GLES30.glUniformMatrix4fv(
            GLES30.glGetUniformLocation(starProgram, "uVp"),
            1, false, viewProjection, 0
        )
        GLES30.glUniform1f(GLES30.glGetUniformLocation(starProgram, "uPointSize"), pointSize)
        GLES30.glUniform4f(
            GLES30.glGetUniformLocation(starProgram, "uColor"),
            color[0], color[1], color[2], color[3]
        )
        buffer.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, buffer)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, count)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDepthMask(true)
    }

    /** A light, schematic dust disk cues the shared early-system epoch from the outside. */
    private fun drawFormationDisk() {
        val age = deepTimeAgeGa
        if (age < 3.85 || age > 4.60) return
        val progress = DeepTimeHistory.systemFormationProgress(age)
        val opacity = (0.10f + 0.34f * progress).coerceIn(0f, 0.44f)
        GLES30.glUseProgram(lineProgram)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(lineProgram, "uVp"), 1, false, viewProjection, 0)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(lineProgram, "uOffset"), 0f, 0f, 0f)
        GLES30.glUniform4f(GLES30.glGetUniformLocation(lineProgram, "uColor"), 0.92f, 0.48f, 0.19f, opacity)
        GLES30.glDepthMask(false)
        GLES30.glEnable(GLES30.GL_BLEND)
        for (buffer in formationDiskBuffers) {
            buffer.position(0)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, buffer)
            GLES30.glDrawArrays(GLES30.GL_LINE_STRIP, 0, buffer.capacity() / 3)
            GLES30.glDisableVertexAttribArray(0)
        }
        GLES30.glDepthMask(true)
    }

    private fun buildFormationDiskBuffers() {
        formationDiskBuffers.clear()
        val segments = 144
        for (ring in 0 until 7) {
            val radius = 2.0 + ring * 4.35
            val values = FloatArray((segments + 1) * 3)
            for (i in 0..segments) {
                val angle = 2.0 * PI * i / segments
                values[i * 3] = (cos(angle) * radius).toFloat()
                values[i * 3 + 1] = (sin(angle * 3.0 + ring) * .025).toFloat()
                values[i * 3 + 2] = (sin(angle) * radius).toFloat()
            }
            formationDiskBuffers += floatBuffer(values)
        }
    }

    private fun updateProjectionForApproach() {
        val body = selectedId?.let { byId[it] }
        val close = body != null && distance < body.radius * 1.35
        val near = if (close) 0.001f else 0.03f
        Matrix.perspectiveM(
            projection, 0,
            if (close) 58f else 48f,
            width.toFloat() / height,
            near,
            300f
        )
    }

    private fun updateApproachSnapshot() {
        val body = selectedId?.let { byId[it] }
        if (body == null) {
            latestApproach = ApproachSnapshot(null, Double.POSITIVE_INFINITY, "SPACE", false)
            return
        }

        val radialDistance = (cameraPosition - body.position).length()
        val altitudeUnits = max(0.0, radialDistance - body.radius)
        val altitudeKm = if (body.radiusKm > 0.0) {
            altitudeUnits / body.radius * body.radiusKm
        } else {
            Double.POSITIVE_INFINITY
        }

        val stage = when (body.id) {
            "mars" -> when {
                altitudeKm > 1500.0 -> "ORBIT"
                altitudeKm > 180.0 -> "CLOSE APPROACH"
                altitudeKm > 25.0 -> "ATMOSPHERE"
                else -> "SURFACE SKIM"
            }
            "moon" -> when {
                altitudeKm > 900.0 -> "ORBIT"
                altitudeKm > 120.0 -> "CLOSE APPROACH"
                altitudeKm > 15.0 -> "LOW ORBIT"
                else -> "SURFACE SKIM"
            }
            "ceres" -> if (altitudeKm < 500.0) "BELT FLYBY" else "ORBIT"
            "sun" -> if (radialDistance < body.radius * 3.2) "CORONA VIEW" else "ORBIT"
            else -> if (radialDistance < body.radius * 3.0) "CLOSE FLYBY" else "ORBIT"
        }

        latestApproach = ApproachSnapshot(
            bodyId = body.id,
            altitudeKm = altitudeKm,
            stage = stage,
            closeLod = when (body.id) {
                "mars" -> altitudeKm < 2500.0
                "moon" -> altitudeKm < 1500.0
                else -> false
            }
        )
    }

    private fun drawMarsAtmosphere(body: CelestialBody) {
        if (latestApproach.bodyId != "mars") return

        val altitude = latestApproach.altitudeKm
        val opacity = when {
            altitude > 2500.0 -> 0.08f
            altitude > 300.0 -> 0.13f
            altitude > 80.0 -> 0.20f
            else -> 0.27f
        }

        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDepthMask(false)

        buildBodyModel(body, 1.022f, 0f)
        GLES30.glUseProgram(planetProgram)
        bindPlanetCommon(body)
        GLES30.glUniform4f(
            GLES30.glGetUniformLocation(planetProgram, "uColor"),
            .92f, .38f, .18f, 1f
        )
        bindTexture(0, 0)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(planetProgram, "uTexture"), 0)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(planetProgram, "uUseTexture"), 0)
        bindTexture(1, 0)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(planetProgram, "uNightTexture"), 1)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(planetProgram, "uUseNight"), 0)
        bindTexture(2, 0)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(planetProgram, "uNormalTexture"), 2)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(planetProgram, "uUseNormal"), 0)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(planetProgram, "uMode"), 3)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(planetProgram, "uOpacity"), opacity)
        drawSphereGeometry()

        GLES30.glDepthMask(true)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
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
        marsCloseTexture = loadTextureResource("mars_close")
        marsNormalTexture = loadTextureResource("mars_normal")
        moonCloseTexture = loadTextureResource("moon_close")
        moonNormalTexture = loadTextureResource("moon_normal")
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
uniform sampler2D uNormalTexture;
uniform int uUseTexture;
uniform int uUseNight;
uniform int uUseNormal;
uniform int uCloseMaterial;
uniform int uMode;
uniform float uOpacity;
uniform float uHistoryLava;
uniform float uHistoryIce;
uniform float uHistoryOcean;
uniform float uHistoryAtmosphere;
uniform float uHistoryFuture;
uniform float uHistoryImpact;
uniform float uHistoryWater;
uniform float uHistoryBasalt;

out vec4 fragColor;

void main() {
    vec4 texel = uUseTexture == 1
        ? (uCloseMaterial > 0 ? textureLod(uTexture, vUv, 0.0) : texture(uTexture, vUv))
        : uColor;

    if (uHistoryLava > 0.001) {
        vec3 magma = vec3(1.0, 0.20, 0.035);
        texel.rgb = mix(texel.rgb, magma + texel.rgb * 0.35, uHistoryLava * 0.92);
    }
    if (uHistoryIce > 0.001) {
        vec3 ice = vec3(0.70, 0.86, 1.0);
        texel.rgb = mix(texel.rgb, ice, uHistoryIce * 0.62);
    }
    if (uHistoryOcean < 0.99 && uHistoryLava < 0.01) {
        float luminance = dot(texel.rgb, vec3(0.299, 0.587, 0.114));
        vec3 drySurface = vec3(luminance * 0.76, luminance * 0.68, luminance * 0.52);
        texel.rgb = mix(drySurface, texel.rgb, uHistoryOcean);
    }
    if (uHistoryAtmosphere < 0.99) {
        texel.rgb *= mix(0.82, 1.0, uHistoryAtmosphere);
    }
    if (uHistoryWater > 0.001) {
        // Schematic early-water cue. It deliberately avoids reconstructed coastlines.
        float luminance = dot(texel.rgb, vec3(0.299, 0.587, 0.114));
        float lowAlbedo = 1.0 - smoothstep(0.24, 0.64, luminance);
        float latitude = abs(vUv.y - 0.5) * 2.0;
        float lowLatitude = 1.0 - smoothstep(0.48, 0.96, latitude);
        float waterMask = (0.28 + 0.72 * lowAlbedo) * lowLatitude;
        texel.rgb = mix(texel.rgb, vec3(0.055, 0.19, 0.25), waterMask * uHistoryWater * 0.64);
    }
    if (uHistoryBasalt > 0.001) {
        // Non-geographic procedural mare cue for the lunar volcanic epoch.
        float mareNoise = 0.5 + 0.5 * sin(vUv.x * 28.0 + sin(vUv.y * 19.0) * 2.2);
        float mareMask = smoothstep(0.60, 0.84, mareNoise);
        mareMask *= 1.0 - smoothstep(0.66, 0.96, abs(vUv.y - 0.5) * 2.0);
        texel.rgb = mix(texel.rgb, texel.rgb * vec3(0.42, 0.46, 0.54), mareMask * uHistoryBasalt * 0.78);
    }
    if (uHistoryImpact > 0.001) {
        // Schematic flash and ejecta ring: a visual event cue, not a geographic reconstruction.
        vec2 impactOffset = vUv - vec2(0.5, 0.5);
        impactOffset.x = mod(impactOffset.x + 0.5, 1.0) - 0.5;
        float impactRadius = length(impactOffset * vec2(2.0, 1.0));
        float flash = 1.0 - smoothstep(0.012, 0.045, impactRadius);
        float ejectaRing = 1.0 - smoothstep(0.008, 0.019, abs(impactRadius - 0.085));
        texel.rgb = mix(texel.rgb, vec3(1.0, 0.30, 0.055), flash * uHistoryImpact * 0.92);
        texel.rgb = mix(texel.rgb, vec3(0.76, 0.28, 0.12), ejectaRing * uHistoryImpact * 0.78);
    }
    if (uHistoryFuture > 0.001) {
        texel.rgb = mix(texel.rgb, vec3(1.0, 0.28, 0.055), uHistoryFuture * 0.82);
    }

    if (uMode == 1) {
        float cloud = dot(texel.rgb, vec3(0.333333));
        if (cloud < 0.025) discard;
        fragColor = vec4(texel.rgb, cloud * uOpacity);
        return;
    }

    vec3 N = normalize(vNormal);
    vec3 L = normalize(-vWorld);
    vec3 V = normalize(uCamera - vWorld);

    if (uMode == 3) {
        float rim = pow(1.0 - max(dot(N, V), 0.0), 2.0);
        float daylight = 0.32 + 0.68 * max(dot(N, L), 0.0);
        float alpha = rim * uOpacity;
        if (alpha < 0.003) discard;
        fragColor = vec4(uColor.rgb * daylight, alpha);
        return;
    }

    if (uMode == 2) {
        fragColor = vec4(texel.rgb, uOpacity);
        return;
    }

    if (uUseNormal == 1) {
        vec3 mapped = textureLod(uNormalTexture, vUv, 0.0).xyz * 2.0 - 1.0;

        // Both close maps are real elevation-derived normal material. Lunar
        // relief gets a slightly harder response because there is no
        // atmosphere to soften crater rims and basin edges.
        mapped.xy *= uCloseMaterial == 2 ? 2.05 : 1.65;

        vec3 axis = abs(N.y) > 0.95 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0);
        vec3 T = normalize(cross(axis, N));
        vec3 B = normalize(cross(N, T));
        N = normalize(T * mapped.x + B * mapped.y + N * mapped.z);
    }


    float ndl = max(dot(N, L), 0.0);
    float facing = max(dot(N, V), 0.0);
    float rim = pow(1.0 - facing, 3.0);

    // Mars keeps a soft atmospheric fill at low altitude. The Moon is
    // intentionally airless: lower ambient light and no atmospheric fill make
    // relief readable without pretending there is lunar haze.
    float ambient = uCloseMaterial == 2 ? 0.15 : (uUseNormal == 1 ? 0.30 : 0.075);
    float light = ambient + (1.0 - ambient) * ndl;
    if (uCloseMaterial == 1) {
        float atmosphericFill = 0.30 + 0.18 * facing;
        light = max(light, atmosphericFill);
    } else if (uCloseMaterial == 2) {
        // Airless lunar fill approximates camera exposure plus weak Earthshine.
        // Direct sunlight still reaches 1.0, so the terminator remains strongly
        // contrasted without making low orbit an unreadable near-black frame.
        float lunarFill = 0.26 + 0.08 * facing;
        light = max(light, lunarFill);
        texel.rgb = pow(max(texel.rgb, vec3(0.0)), vec3(0.90));
    }
    light = mix(light, 1.0, uEmissive);

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
uniform float uPointSize;

void main() {
    gl_Position = uVp * vec4(aPosition, 1.0);
    gl_PointSize = uPointSize;
}
"""

        private const val STAR_FRAGMENT_SHADER = """#version 300 es
precision mediump float;
uniform vec4 uColor;

out vec4 fragColor;

void main() {
    vec2 p = gl_PointCoord - vec2(0.5);
    float d = dot(p, p);
    if (d > 0.25) discard;
    float a = 1.0 - smoothstep(0.08, 0.25, d);
    fragColor = vec4(uColor.rgb, uColor.a * a);
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
