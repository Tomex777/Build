package com.night.endless.engine.render

import android.app.ActivityManager
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
import com.night.endless.engine.scene.DeepTimeHistory
import com.night.endless.engine.scene.KeplerEphemeris
import com.night.endless.engine.scene.GiantSatelliteOrbit
import com.night.endless.engine.scene.LunarOrbitApproximation
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
    private var marsSkyProgram = 0

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
    private var extendedSurfaceId: String? = null
    private var extendedSurfaceX = 0.0
    private var extendedSurfaceZ = 0.0
    private val extendedTerrains = mutableMapOf<String, ExtendedSurfaceTerrain>()
    private var surfaceForwardInput = 0f
    private var surfaceStrafeInput = 0f
    // Input lifetime uses monotonic wall time, not GL frame availability.
    // Software GLES can stall while a player holds a thumb; without this clock,
    // releasing between frames discards the entire movement gesture.
    private var lastSurfaceMovementNanos = System.nanoTime()
    @Volatile private var deepTimeAgeGa = 0.0
    @Volatile private var renderedDeepTimeAgeGa = 0.0
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
        val extendedSurfaceId: String? = null,
        val extendedSurfaceX: Double = 0.0,
        val extendedSurfaceZ: Double = 0.0
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
        // Giant-planet moon orbits are schematic rather than JPL ephemerides.
        bodies += CelestialBody("europa", "Europa", .19, 1.98, 3.55118, 85.23,
            floatArrayOf(.67f,.69f,.63f,1f), parentId = "jupiter",
            phaseRad = 1.6, radiusKm = 1560.8, axialTiltDeg = .1)
        bodies += CelestialBody("titan", "Titan", .23, 1.93, 15.945, 382.68,
            floatArrayOf(.73f,.45f,.19f,1f), parentId = "saturn",
            phaseRad = .6, radiusKm = 2574.73, axialTiltDeg = .3)
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

    @Volatile private var activeDepthBits = 0
    fun depthBufferBits(): Int = activeDepthBits

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // A new EGL context invalidates all previous program handles,
        // including numeric IDs that a driver may reuse after context loss.
        cachedUniforms.clear()
        val depth = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_DEPTH_BITS, depth, 0)
        activeDepthBits = depth[0]
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
        marsSkyProgram = createProgram(MARS_SKY_VERTEX_SHADER, MARS_SKY_FRAGMENT_SHADER)

        loadPlanetTextures()
        buildStars()
        asteroidBeltBuffer = floatBuffer(AsteroidBeltModel.positions(asteroidBeltOrbits, 0.0))
        buildOrbitBuffers()
        buildFormationDiskBuffers()
        buildRingMesh()
        // NASA DEM assets must be loaded BEFORE any coarse geometry or
        // cached local LOD is constructed. Previous order silently built
        // the Mars coarse surface using its procedural fallback while the
        // walking camera sampled NASA MOLA -- producing wrong horizons.
        MoonSurfaceMeshGenerator.elevationPatch =
            MeasuredElevationPatch.load(context, "moon_lola_terrain.bin")
        MarsSurfaceTerrain.elevationPatch =
            MeasuredElevationPatch.load(context, "mars_mola_terrain.bin")
        // GL handles must be recreated after context loss: never retain
        // shader IDs or buffers belonging to an old EGL context.
        extendedTerrains.clear()
        marsSurfaceTerrain = MarsSurfaceTerrain(context)
        moonSurfaceTerrain = MoonSurfaceTerrain(context)
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = max(1, w)
        height = max(1, h)
        GLES30.glViewport(0, 0, width, height)
        Matrix.perspectiveM(projection, 0, 48f, width.toFloat() / height, 0.03f, 300f)
    }

    data class UiFrameSnapshot(
        val timeMillis: Long,
        val speedLabel: String,
        val labels: List<BodyLabelSnapshot>,
        val approach: ApproachSnapshot,
        val navigation: ExplorationState,
        val navigationRevision: Long,
        val surfaceBody: String?
    )

    @Volatile private var navigationRevision = 0L
    fun currentNavigationRevision(): Long = navigationRevision

    @Volatile private var completedUiFrame: UiFrameSnapshot? = null
    fun uiFrameSnapshot(): UiFrameSnapshot? = completedUiFrame

    private fun publishUiFrame() {
        completedUiFrame = UiFrameSnapshot(
            timeMillis = clock.currentTimeMillis(), speedLabel = clock.speedLabel(),
            labels = latestLabels, approach = latestApproach,
            navigation = snapshotState(), navigationRevision = navigationRevision,
            surfaceBody = completedSurfaceBody
        )
    }

    @Volatile private var completedSurfaceBody: String? = null
    /** Surface represented by the last completed GL frame, not requested mode. */
    fun renderedSurfaceBodyId(): String? = completedSurfaceBody

    // UI navigation/camera mutators use this same monitor. A landing must
    // never change mode and yaw halfway through an orbital draw.
    @Synchronized
    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        // Three independent clocks: actual frame elapsed for the
        // astronomical simulation, bounded smoothing for camera movement,
        // and short physics steps for collisions. The old 50ms clamp
        // slowed simulated calendar time by 10-20x on software/low-end GLES.
        val realDt = RendererFrameTiming.elapsedSeconds(lastNanos, now)
        val dt = realDt.coerceAtMost(.12)
        lastNanos = now

        applyControlDamping(dt)
        updateSurfaceMovement(now)

        val simSeconds = clock.advance(realDt)
        updateBodyPositions(simSeconds)

        cloudRotation += dt * .012
        venusCloudRotation -= dt * .004

        if (marsSurfaceMode) {
            latestLabels = emptyList()
            drawMarsSurfaceFrame()
            completedSurfaceBody = "mars"
            renderedDeepTimeAgeGa = deepTimeAgeGa
            publishUiFrame()
            completedFrames++
            return
        }
        if (moonSurfaceMode) {
            latestLabels = emptyList()
            drawMoonSurfaceFrame()
            completedSurfaceBody = "moon"
            renderedDeepTimeAgeGa = deepTimeAgeGa
            publishUiFrame()
            completedFrames++
            return
        }
        extendedSurfaceId?.let { id ->
            latestLabels = emptyList()
            drawExtendedSurfaceFrame(id)
            completedSurfaceBody = id
            renderedDeepTimeAgeGa = deepTimeAgeGa
            publishUiFrame()
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
                val atmosphere = DeepTimeHistory.venusVisualState(deepTimeAgeGa).atmosphere.coerceIn(0f, 1f)
                if (atmosphere > .01f) {
                    drawOverlaySphere(
                        body,
                        venusAtmosphereTexture,
                        1.018f,
                        venusCloudRotation.toFloat(),
                        2,
                        .82f * atmosphere
                    )
                }
            }
            if (body.id == "earth" || body.id == "venus" || body.id == "mars") {
                drawPlanetAtmosphere(body)
            }
        }

        drawSaturnRing()
        completedSurfaceBody = null
        renderedDeepTimeAgeGa = deepTimeAgeGa
        publishUiFrame()
        completedFrames++
    }

    fun completedFrameCount(): Long = completedFrames

    /**
     * Last deep-time age acknowledged by a completed GL frame. This lets runtime
     * validation wait for the requested visual epoch instead of guessing from an
     * arbitrary number of frames on slower software renderers.
     */
    fun renderedDeepTimeAgeGa(): Double = renderedDeepTimeAgeGa

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
        extendedSurfaceId = extendedSurfaceId,
        extendedSurfaceX = extendedSurfaceX,
        extendedSurfaceZ = extendedSurfaceZ
    )

    @Synchronized
    fun restoreState(state: ExplorationState) {
        navigationRevision++
        clock.restore(state.clockState)
        updateBodyPositions(clock.seconds())

        val restoreSurfaceBody = when {
            state.moonSurfaceMode -> "moon"
            state.marsSurfaceMode -> "mars"
            state.extendedSurfaceId != null && ExtendedSurfaceMesh.isSupported(state.extendedSurfaceId) -> state.extendedSurfaceId
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
        extendedSurfaceId = restoreSurfaceBody?.takeIf { ExtendedSurfaceMesh.isSupported(it) }
        extendedSurfaceX = (state.extendedSurfaceX.takeIf { it.isFinite() } ?: 0.0).coerceIn(-8.0, 8.0)
        extendedSurfaceZ = (state.extendedSurfaceZ.takeIf { it.isFinite() } ?: 0.0).coerceIn(-8.0, 8.0)
        pendingYaw = 0.0
        pendingPitch = 0.0
        surfaceForwardInput = 0f
        surfaceStrafeInput = 0f

        cameraTarget = selectedId?.let { byId[it]?.position } ?: Vec3d.ZERO
        val cp = cos(pitch)
        cameraPosition = cameraTarget + Vec3d(
            cos(yaw) * cp * distance,
            sin(pitch) * distance,
            sin(yaw) * cp * distance
        )
        previousCameraPosition = cameraPosition
        lastNanos = System.nanoTime()
        lastSurfaceMovementNanos = lastNanos
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
        if (isSurfaceMode()) {
            yaw += dx.coerceIn(-120f, 120f) * (2.0 * PI / h.toDouble()) * 0.22
            // A 24° pitch limit made the sky overhead impossible to inspect
            // and nearly hid Tycho's Earth (~45° up). Allow a natural
            // first-person view without crossing the 90° gimbal pole.
            pitch = (pitch - dy.coerceIn(-120f, 120f) *
                (2.0 * PI / h.toDouble()) * 0.22).coerceIn(-1.30, 1.30)
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
        if (isSurfaceMode() || !scaleFactor.isFinite() || scaleFactor <= 0f) return
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
            "venus" -> body.radius * 1.09
            "ceres" -> body.radius * 1.25
            "mercury" -> body.radius * 1.025
            "europa" -> body.radius * 1.035
            "titan" -> body.radius * 1.095
            else -> return
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
            "venus" -> body.radius * 1.03
            "mercury", "ceres", "europa", "titan" -> body.radius * 1.003
            else -> return
        }
    }

    @Synchronized
    fun pullBackSelected() {
        val body = selectedId?.let { byId[it] } ?: return
        targetDistance = max(body.radius * 7.5, 2.0)
    }

    @Synchronized
    fun isSurfaceMode(): Boolean = marsSurfaceMode || moonSurfaceMode || extendedSurfaceId != null

    @Synchronized
    fun surfaceBodyId(): String? = when {
        marsSurfaceMode -> "mars"
        moonSurfaceMode -> "moon"
        else -> extendedSurfaceId
    }

    @Synchronized
    fun landOnMars(): Boolean {
        if (isSurfaceMode()) return false
        if (selectedId != "mars" || latestApproach.stage != "SURFACE SKIM") return false
        moonSurfaceMode = false
        // The original default landed directly on the smooth caldera lip
        // facing *down* an invisible basin: the ground looked flat despite
        // having real MOLA slopes. Spawn 10.5km east and 12km south of the
        // same registered NASA center, on the actual lower caldera floor.
        // Facing the measured rim reveals genuine 1-2km wall relief.
        marsSurfaceX = 3.5
        marsSurfaceZ = -4.0
        marsSurfaceMode = true
        surfaceForwardInput = 0f
        surfaceStrafeInput = 0f
        lastSurfaceMovementNanos = System.nanoTime()
        pendingYaw = 0.0
        pendingPitch = 0.0
        yaw = -1.40
        pitch = 0.04
        return true
    }

    @Synchronized
    fun landOnMoon(): Boolean {
        if (isSurfaceMode()) return false
        if (selectedId != "moon" || latestApproach.stage != "SURFACE SKIM") return false
        marsSurfaceMode = false
        moonSurfaceX = 1.5
        moonSurfaceZ = -0.5
        moonSurfaceMode = true
        surfaceForwardInput = 0f
        surfaceStrafeInput = 0f
        lastSurfaceMovementNanos = System.nanoTime()
        pendingYaw = 0.0
        pendingPitch = 0.0
        yaw = 0.72
        // The first landed frame should show nearby relief without snapping the
        // user's heading away from the approach direction.
        pitch = -0.09
        return true
    }

    @Synchronized
    fun landOnExtendedBody(id: String): Boolean {
        if (isSurfaceMode() || !ExtendedSurfaceMesh.isSupported(id)) return false
        if (selectedId != id || latestApproach.stage != "SURFACE SKIM") return false
        marsSurfaceMode = false
        moonSurfaceMode = false
        extendedSurfaceId = id
        extendedSurfaceX = 0.0
        extendedSurfaceZ = 0.0
        surfaceForwardInput = 0f
        surfaceStrafeInput = 0f
        lastSurfaceMovementNanos = System.nanoTime()
        pendingYaw = 0.0
        pendingPitch = 0.0
        yaw = if (id == "europa") 1.24 else .85
        pitch = if (id == "titan") -.11 else -.08
        return true
    }

    /**
     * GTA-style analog movement follows a monotonic input clock, not the
     * renderer's frame rate. Release commits the held gesture, while
     * cancellation discards it, including during lifecycle transitions.
     */
    @Synchronized
    fun setSurfaceMovementInput(forward: Float, strafe: Float) {
        // Apply motion up to this touch event before updating its direction.
        // Crucial when finger-down and finger-up occur between slow GL frames.
        updateSurfaceMovement(System.nanoTime())
        if (!isSurfaceMode()) {
            surfaceForwardInput = 0f
            surfaceStrafeInput = 0f
            return
        }
        val length = if (forward.isFinite() && strafe.isFinite())
            kotlin.math.hypot(forward, strafe) else 0f
        val divisor = length.coerceAtLeast(1f)
        surfaceForwardInput = if (length < 0.001f) 0f else forward / divisor
        surfaceStrafeInput = if (length < 0.001f) 0f else strafe / divisor
    }

    @Synchronized
    fun cancelSurfaceMovementInput() {
        // App pause/detach is different from a completed swipe: do not advance
        // the astronaut during a background transition.
        surfaceForwardInput = 0f
        surfaceStrafeInput = 0f
        lastSurfaceMovementNanos = System.nanoTime()
    }

    private fun updateSurfaceMovement(nowNanos: Long) {
        val elapsed = SurfaceJoystickMath.elapsedMovementSeconds(lastSurfaceMovementNanos, nowNanos)
        lastSurfaceMovementNanos = nowNanos
        if (!isSurfaceMode()) return
        val forward = surfaceForwardInput
        val strafe = surfaceStrafeInput
        val magnitude = kotlin.math.hypot(forward, strafe)
        if (magnitude < 0.001f || elapsed <= 0.0) return
        // Limit each physics step to 50ms while consuming the whole bounded
        // gesture interval. Avoid both frame-rate-dependent lost input and
        // large collision jumps on slow renderers.
        var remaining = elapsed
        while (remaining > 1e-8) {
            val dt = min(remaining, 0.05)
            val distance = SurfaceJoystickMath.travelWorldUnits(dt) *
                magnitude.coerceAtMost(1f)
            advanceSurfacePosition(forward / magnitude, strafe / magnitude, distance)
            remaining -= dt
        }
    }

    // Kept for saved gesture integrations/tests that request one discrete step.
    @Synchronized
    fun walkSurface(forward: Float, strafe: Float) {
        val len = kotlin.math.hypot(forward, strafe)
        if (!len.isFinite() || len < 0.001f) return
        advanceSurfacePosition(forward / len, strafe / len, 0.00078)
    }

    private fun advanceSurfacePosition(forward: Float, strafe: Float, step: Double) {
        if (!isSurfaceMode() || !step.isFinite() || step <= 0.0) return
        val headingSin = sin(yaw)
        val headingCos = cos(yaw)
        val dx = (headingSin * forward + headingCos * strafe) * step
        val dz = (-headingCos * forward + headingSin * strafe) * step
        val x = when {
            marsSurfaceMode -> marsSurfaceX
            moonSurfaceMode -> moonSurfaceX
            else -> extendedSurfaceX
        }
        val z = when {
            marsSurfaceMode -> marsSurfaceZ
            moonSurfaceMode -> moonSurfaceZ
            else -> extendedSurfaceZ
        }
        // Real walkers cannot step vertically up a 72cm wall in 15cm
        // horizontal travel. Reject excessive grade and slide against
        // angled ridges instead of freezing both axes at a cliff.
        val surface: (Double, Double) -> Double = when {
            marsSurfaceMode -> MarsSurfaceTerrain::renderedHeightAt
            moonSurfaceMode -> MoonSurfaceTerrain::renderedHeightAt
            else -> {
                val bodyId = extendedSurfaceId ?: return
                { px, pz -> ExtendedSurfaceMesh.heightAt(bodyId, px, pz) }
            }
        }
        val next = SurfaceWalkingPhysics.move(x, z, dx, dz, surface)
        when {
            marsSurfaceMode -> {
                marsSurfaceX = next.first
                marsSurfaceZ = next.second
            }
            moonSurfaceMode -> {
                moonSurfaceX = next.first
                moonSurfaceZ = next.second
            }
            else -> {
                extendedSurfaceX = next.first
                extendedSurfaceZ = next.second
            }
        }
    }

    @Synchronized
    fun surfaceCoordinates(): Pair<Double, Double> = when {
        marsSurfaceMode -> marsSurfaceX to marsSurfaceZ
        moonSurfaceMode -> moonSurfaceX to moonSurfaceZ
        extendedSurfaceId != null -> extendedSurfaceX to extendedSurfaceZ
        else -> 0.0 to 0.0
    }

    @Synchronized
    fun surfaceOrientation(): Pair<Double, Double> = yaw to pitch

    @Synchronized
    fun takeOffMars(): Boolean {
        if (!marsSurfaceMode) return false
        marsSurfaceMode = false
        surfaceForwardInput = 0f
        surfaceStrafeInput = 0f
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
        surfaceForwardInput = 0f
        surfaceStrafeInput = 0f
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
        extendedSurfaceId != null -> {
            val body = byId[extendedSurfaceId]
            if (body == null) {
                false
            } else {
                extendedSurfaceId = null
                surfaceForwardInput = 0f
                surfaceStrafeInput = 0f
                lastSurfaceMovementNanos = System.nanoTime()
                targetDistance = body.radius * 1.12
                previousCameraPosition = body.position + Vec3d(0.0, 0.0, targetDistance)
                cameraPosition = previousCameraPosition
                true
            }
        }
        else -> false
    }

    private fun drawMarsSurfaceFrame() {
        GLES30.glClearColor(0.055f, 0.027f, 0.018f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        drawMarsSky()
        Matrix.perspectiveM(projection, 0, 67f, width.toFloat() / height, 0.0002f, 32f)
        val eyeY = MarsSurfaceTerrain.renderedHeightAt(marsSurfaceX, marsSurfaceZ) + 0.00078 // 2.34m eye level
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

    private fun drawMarsSky() = drawSurfaceSky(0)

    /**
     * Draw a directionally consistent sky behind the terrain.
     *
     * No stars in daylight surface exposure, or through Titan's opaque haze.
     * Europa's Jupiter disk uses the actual loaded Jovian orbital texture,
     * with the real 11.9-degree apparent diameter at Europa's mean distance.
     * The landing patch's fixed sub-Jovian camera orientation is illustrative,
     * not a surveyed geographic location or a real-time sky ephemeris.
     *
     * Full-screen GLES3 triangle: no framebuffer allocation, no draw
     * textures beyond the shared 2K planetary atlas.
     */
    private fun drawSurfaceSky(skyWorld: Int) {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)
        GLES30.glUseProgram(marsSkyProgram)
        GLES30.glUniform1i(uniformLocation(marsSkyProgram, "uSkyWorld"), skyWorld)
        GLES30.glUniform1f(uniformLocation(marsSkyProgram, "uPitch"), pitch.toFloat())
        GLES30.glUniform1f(uniformLocation(marsSkyProgram, "uYaw"), yaw.toFloat())
        GLES30.glUniform1f(uniformLocation(marsSkyProgram, "uAspect"), width.toFloat() / height)
        val parent = when (skyWorld) {
            2 -> textures["jupiter"] ?: 0
            3 -> textures["earth"] ?: 0
            else -> 0
        }
        GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, parent)
        GLES30.glUniform1i(uniformLocation(marsSkyProgram, "uParentTexture"), 3)
        GLES30.glUniform1i(uniformLocation(marsSkyProgram, "uHasParentTexture"), if (parent == 0) 0 else 1)
        if (skyWorld == 3) {
            val earthDirection = MoonEarthSky.tychoDirection
            GLES30.glUniform3f(
                uniformLocation(marsSkyProgram, "uEarthDirection"),
                earthDirection[0], earthDirection[1], earthDirection[2]
            )
        }
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glDepthMask(true)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    private fun drawAtmosphereSky(skyWorld: Int) = drawSurfaceSky(skyWorld)

    private fun drawMoonSurfaceFrame() {
        GLES30.glClearColor(0.0015f, 0.0015f, 0.0025f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        Matrix.perspectiveM(projection, 0, 67f, width.toFloat() / height, 0.0002f, 36f)
        val eyeY = MoonSurfaceTerrain.renderedHeightAt(moonSurfaceX, moonSurfaceZ) + 0.00078 // 2.34m eye level
        val lookX = sin(yaw) * cos(pitch)
        val lookY = sin(pitch)
        val lookZ = -cos(yaw) * cos(pitch)
        Matrix.setLookAtM(
            view, 0,
            0f, eyeY.toFloat(), 0f,
            lookX.toFloat(), (eyeY + lookY).toFloat(), lookZ.toFloat(),
            0f, 1f, 0f
        )
        // Tycho's near-side Moon sky can show the Earth above the horizon.
        // Like Apollo daylight exposure, the surrounding sky is starless and
        // nearly black; Earth's actual ~1.9 degree disk is rendered using
        // the same atlas as the planetary view, never as a screen-fixed icon.
        drawSurfaceSky(3)
        // Lunar daylight exposure: sunlit ground needs a much shorter
        // exposure than stars. A camera exposing bright regolith shows an
        // almost black, STARLESS sky (Apollo photographs demonstrate this).
        // Stars remain visible when exploring from orbit.
        Matrix.perspectiveM(projection, 0, 67f, width.toFloat() / height, 0.0002f, 36f)
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)
        moonSurfaceTerrain?.draw(viewProjection, -moonSurfaceX.toFloat(), -moonSurfaceZ.toFloat())
    }

    private fun drawExtendedSurfaceFrame(id: String) {
        val terrain = extendedTerrains.getOrPut(id) { ExtendedSurfaceTerrain(context,id) }
        // Titan has an optically thick haze. Airless bodies are black;
        // starless exposure follows illuminated Apollo-style photography.
        if (id == "titan") GLES30.glClearColor(.28f,.175f,.09f,1f)
        else GLES30.glClearColor(.0015f,.0015f,.0025f,1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        if (id == "titan") drawAtmosphereSky(1)
        else if (id == "europa") drawAtmosphereSky(2)
        Matrix.perspectiveM(projection,0,67f,width.toFloat()/height,.0002f,36f)
        val eyeY = terrain.groundHeightAt(extendedSurfaceX,extendedSurfaceZ)+.00078
        val lx=sin(yaw)*cos(pitch);val ly=sin(pitch);val lz=-cos(yaw)*cos(pitch)
        Matrix.setLookAtM(view,0,0f,eyeY.toFloat(),0f,
            lx.toFloat(),(eyeY+ly).toFloat(),lz.toFloat(),0f,1f,0f)
        Matrix.multiplyMM(viewProjection,0,projection,0,view,0)
        terrain.draw(viewProjection,-extendedSurfaceX.toFloat(),-extendedSurfaceZ.toFloat())
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
        navigationRevision++
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
        navigationRevision++
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
            if (!isBodyFormed(body.id, deepTimeAgeGa)) continue
            // Use the rendered oblate ellipsoid rather than the old spherical
            // approximation; taps at Jupiter's flattened poles must not
            // select empty sky or another object behind it.
            val hit = PlanetShape.rayHitDistance(
                body.id, origin - body.position, dir,
                body.radius, body.axialTiltDeg
            ) ?: continue
            if (best == null || hit < best!!.second) best = body to hit
        }

        best?.first?.let { focus(it.id) }
    }

    @Synchronized
    private fun applyControlDamping(dt: Double) {
        val frameScale = (dt * 60.0).coerceIn(0.0, 8.0)
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
            1.0 - (1.0 - perFrameTargetDamping).pow((dt * 60.0).coerceIn(0.0, 8.0))

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
                // Sphere collision is conservative for a tilted ellipsoid:
                // enclose its entire equatorial bulge so approach cameras
                // cannot clip through the actual Jovian cloud boundary.
                val minDistance = body.radius *
                    PlanetShape.equatorialScale(body.id) + cameraRadius + skin
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
        val utcMillis = clock.currentTimeMillis()
        val map = HashMap<String, CelestialBody>()
        for (body in bodies) {
            body.position = when {
                body.id == "sun" -> Vec3d.ZERO
                // Ceres now follows a two-body elliptical/inclined orbit
                // based on NASA/JPL Small-Body Database elements. The orbit
                // is approximate and not a high-precision SPICE trajectory.
                body.id == "europa" || body.id == "titan" -> {
                    // The moon's orbital plane is aligned with the
                    // giant's actual rendered equator (and Saturn's rings).
                    // Former sin(angle*.47) vertical wobble did not close
                    // each orbit and diverged from the flat drawn orbit line.
                    val angle = body.phaseRad + 2.0 * PI * days / body.orbitPeriodDays
                    val parent = map[body.parentId]
                    (parent?.position ?: Vec3d.ZERO) +
                        GiantSatelliteOrbit.position(
                            body.orbitRadius, angle, parent?.axialTiltDeg ?: 0.0
                        )
                }
                body.id == "moon" -> {
                    // The SAME eccentric, inclined, nodally precessing model
                    // now generates the visual orbit guide and the actual
                    // Moon's Earth-relative position, so neither contradicts
                    // the other when inspecting Earth's satellite.
                    (map["earth"]?.position ?: Vec3d.ZERO) +
                        LunarOrbitApproximation.relativePosition(utcMillis, body.orbitRadius)
                }
                else -> KeplerEphemeris.position(body.id, utcMillis, body.orbitRadius)
                    ?: Vec3d.ZERO
            }
            map[body.id] = body
        }
        collision.replaceSpheres(
            bodies.filter { isBodyFormed(it.id, deepTimeAgeGa) }
                .map { SphereCollider(
                    it.id, it.position,
                    it.radius * PlanetShape.equatorialScale(it.id)
                ) }
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
                        latestApproach.stage == "CLOUD TOPS" ||
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
        GLES30.glUniform1i(uniformLocation(planetProgram, "uTexture"), 0)
        GLES30.glUniform1i(
            uniformLocation(planetProgram, "uUseTexture"),
            if (baseTexture != 0) 1 else 0
        )

        val night = if (body.id == "earth" && deepTimeAgeGa <= 0.0001) earthNightTexture else 0
        bindTexture(1, night)
        GLES30.glUniform1i(uniformLocation(planetProgram, "uNightTexture"), 1)
        GLES30.glUniform1i(
            uniformLocation(planetProgram, "uUseNight"),
            if (night != 0) 1 else 0
        )

        val normal = when {
            useMarsClose -> marsNormalTexture
            useMoonClose -> moonNormalTexture
            else -> 0
        }
        bindTexture(2, normal)
        GLES30.glUniform1i(uniformLocation(planetProgram, "uNormalTexture"), 2)
        GLES30.glUniform1i(
            uniformLocation(planetProgram, "uUseNormal"),
            if (normal != 0) 1 else 0
        )
        GLES30.glUniform1i(
            uniformLocation(planetProgram, "uCloseMaterial"),
            when {
                useMarsClose -> 1
                useMoonClose -> 2
                else -> 0
            }
        )

        GLES30.glUniform1i(uniformLocation(planetProgram, "uMode"), 0)
        GLES30.glUniform1f(uniformLocation(planetProgram, "uOpacity"), 1f)
        val historyState = when (body.id) {
            "earth" -> DeepTimeHistory.earthVisualState(deepTimeAgeGa)
            "mercury" -> DeepTimeHistory.mercuryVisualState(deepTimeAgeGa)
            "venus" -> DeepTimeHistory.venusVisualState(deepTimeAgeGa)
            "mars" -> DeepTimeHistory.marsVisualState(deepTimeAgeGa)
            "moon" -> DeepTimeHistory.moonVisualState(deepTimeAgeGa)
            "jupiter" -> DeepTimeHistory.jupiterVisualState(deepTimeAgeGa)
            "saturn" -> DeepTimeHistory.saturnVisualState(deepTimeAgeGa)
            "uranus" -> DeepTimeHistory.uranusVisualState(deepTimeAgeGa)
            "neptune" -> DeepTimeHistory.neptuneVisualState(deepTimeAgeGa)
            else -> null
        }
        GLES30.glUniform1f(uniformLocation(planetProgram, "uHistoryLava"), historyState?.lava ?: 0f)
        GLES30.glUniform1f(uniformLocation(planetProgram, "uHistoryIce"), historyState?.ice ?: 0f)
        GLES30.glUniform1f(uniformLocation(planetProgram, "uHistoryOcean"), historyState?.ocean ?: 1f)
        GLES30.glUniform1f(uniformLocation(planetProgram, "uHistoryAtmosphere"), historyState?.atmosphere ?: 1f)
        GLES30.glUniform1f(uniformLocation(planetProgram, "uHistoryImpact"), historyState?.impact ?: 0f)
        GLES30.glUniform1f(uniformLocation(planetProgram, "uHistoryWater"), historyState?.water ?: 0f)
        GLES30.glUniform1f(uniformLocation(planetProgram, "uHistoryBasalt"), historyState?.basalt ?: 0f)
        GLES30.glUniform1f(
            uniformLocation(planetProgram, "uHistoryFuture"),
            if (body.id == "sun" && deepTimeAgeGa < 0.0 && deepTimeAgeGa > -6.0) (-deepTimeAgeGa / 5.0).toFloat().coerceIn(0f, 1f) else 0f
        )

        drawSphereGeometry()
        GLES30.glUniform1f(uniformLocation(planetProgram, "uHistoryLava"), 0f)
        GLES30.glUniform1f(uniformLocation(planetProgram, "uHistoryIce"), 0f)
        GLES30.glUniform1f(uniformLocation(planetProgram, "uHistoryOcean"), 1f)
        GLES30.glUniform1f(uniformLocation(planetProgram, "uHistoryAtmosphere"), 1f)
        GLES30.glUniform1f(uniformLocation(planetProgram, "uHistoryImpact"), 0f)
        GLES30.glUniform1f(uniformLocation(planetProgram, "uHistoryWater"), 0f)
        GLES30.glUniform1f(uniformLocation(planetProgram, "uHistoryBasalt"), 0f)
        GLES30.glUniform1f(uniformLocation(planetProgram, "uHistoryFuture"), 0f)
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
        GLES30.glUniform1i(uniformLocation(planetProgram, "uTexture"), 0)
        GLES30.glUniform1i(uniformLocation(planetProgram, "uUseTexture"), 1)

        bindTexture(1, 0)
        GLES30.glUniform1i(uniformLocation(planetProgram, "uNightTexture"), 1)
        GLES30.glUniform1i(uniformLocation(planetProgram, "uUseNight"), 0)
        bindTexture(2, 0)
        GLES30.glUniform1i(uniformLocation(planetProgram, "uNormalTexture"), 2)
        GLES30.glUniform1i(uniformLocation(planetProgram, "uUseNormal"), 0)
        GLES30.glUniform1i(uniformLocation(planetProgram, "uCloseMaterial"), 0)

        GLES30.glUniform1i(uniformLocation(planetProgram, "uMode"), mode)
        GLES30.glUniform1f(uniformLocation(planetProgram, "uOpacity"), opacity)

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
        // The already-tilted local spin axis encodes the retrograde
        // directions of Venus (177.36°) and Uranus (97.77°). Using their
        // signed rotation periods again would double-reverse both planets.
        // Keep the angle bounded at extremely accelerated simulation times.
        val baseRotation = PlanetarySpin.angleDegrees(
            elapsedHours, body.rotationHours
        )

        Matrix.rotateM(
            model, 0,
            (baseRotation + Math.toDegrees(extraRotation.toDouble())).toFloat(),
            0f, 1f, 0f
        )

        // Keep the simulation's volume-equivalent body radius while
        // rendering measured polar flattening of the four giant planets.
        // Their cloud tops are not spheres (Jupiter b/a is roughly 0.935).
        val r = (body.radius * radiusMultiplier).toFloat()
        Matrix.scaleM(
            model, 0,
            r * PlanetShape.equatorialScale(body.id),
            r * PlanetShape.polarScale(body.id),
            r * PlanetShape.equatorialScale(body.id)
        )

        Matrix.multiplyMM(mvp, 0, viewProjection, 0, model, 0)
    }

    private fun bindPlanetCommon(body: CelestialBody) {
        GLES30.glUniformMatrix4fv(
            uniformLocation(planetProgram, "uMvp"),
            1, false, mvp, 0
        )
        GLES30.glUniformMatrix4fv(
            uniformLocation(planetProgram, "uModel"),
            1, false, model, 0
        )
        GLES30.glUniform4fv(
            uniformLocation(planetProgram, "uColor"),
            1, body.color, 0
        )
        GLES30.glUniform3f(
            uniformLocation(planetProgram, "uCamera"),
            cameraPosition.x.toFloat(),
            cameraPosition.y.toFloat(),
            cameraPosition.z.toFloat()
        )
        // Vertex normal must be the inverse-transpose equivalent for an
        // oblate ellipsoid; treating Jupiter as a scaled sphere mislights
        // its polar clouds and creates a false circular highlight.
        GLES30.glUniform1f(
            uniformLocation(planetProgram, "uPolarRatio"),
            PlanetShape.polarRatio(body.id)
        )
        GLES30.glUniform1f(
            uniformLocation(planetProgram, "uEmissive"),
            if (body.id == "sun") 1f else 0f
        )
        GLES30.glUniform1i(
            uniformLocation(planetProgram, "uPlanetKind"),
            when (body.id) {
                "earth" -> 1
                "jupiter", "saturn", "uranus", "neptune" -> 2
                else -> 0
            }
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
        orbitBuffers.clear()
        orbitCounts.clear()
        for (body in bodies) {
            if (body.id == "sun") continue
            val segments = if (body.parentId != null) 96 else 180
            val jpl = KeplerEphemeris.orbitPoints(
                body.id, clock.currentTimeMillis(), body.orbitRadius, segments
            )
            val values = when {
                body.id == "moon" -> {
                    LunarOrbitApproximation.orbitPolyline(
                        clock.currentTimeMillis(), body.orbitRadius, segments
                    )
                }
                body.id == "europa" || body.id == "titan" -> {
                    // Giant satellites and their displayed orbit lines share
                    // an inclined equatorial plane with the ring/planet model.
                    val parentTilt = body.parentId?.let { byId[it]?.axialTiltDeg } ?: 0.0
                    GiantSatelliteOrbit.polyline(body.orbitRadius, parentTilt, segments)
                }
                else -> {
                jpl ?: FloatArray(segments * 3).also { out ->
                    for (i in 0 until segments) {
                        val angle = i.toDouble() / segments * 2.0 * PI
                        out[i * 3] = (cos(angle) * body.orbitRadius).toFloat()
                        out[i * 3 + 1] = 0f
                        out[i * 3 + 2] = (sin(angle) * body.orbitRadius).toFloat()
                    }
                }
                }
            }
            orbitBuffers[body.id] = floatBuffer(values)
            orbitCounts[body.id] = segments
        }
    }

    private fun drawOrbits() {
        GLES30.glUseProgram(lineProgram)
        GLES30.glUniformMatrix4fv(
            uniformLocation(lineProgram, "uVp"),
            1, false, viewProjection, 0
        )
        GLES30.glLineWidth(1f)

        for (body in bodies) {
            if (body.id == "sun") continue

            val buffer = orbitBuffers[body.id] ?: continue
            val offset = body.parentId?.let { byId[it]?.position } ?: Vec3d.ZERO

            GLES30.glUniform3f(
                uniformLocation(lineProgram, "uOffset"),
                offset.x.toFloat(), offset.y.toFloat(), offset.z.toFloat()
            )
            GLES30.glUniform4f(
                uniformLocation(lineProgram, "uColor"),
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
        // Saturn's rings lie in its equatorial plane, so their radii must
        // use the same equatorial radius as its newly oblate cloud body.
        val ringRadius = saturn.radius.toFloat() * PlanetShape.equatorialScale("saturn")
        Matrix.scaleM(model, 0, ringRadius, ringRadius, ringRadius)
        Matrix.multiplyMM(mvp, 0, viewProjection, 0, model, 0)

        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDepthMask(false)

        GLES30.glUseProgram(ringProgram)
        GLES30.glUniformMatrix4fv(
            uniformLocation(ringProgram, "uMvp"),
            1, false, mvp, 0
        )
        GLES30.glUniformMatrix4fv(
            uniformLocation(ringProgram, "uModel"),
            1, false, model, 0
        )
        // Solar illumination is from the Sun at world origin. Ringed gas
        // giants cast a real planet-shaped umbra over the far-side annulus;
        // the previous ring texture glowed uniformly through Saturn's night.
        GLES30.glUniform3f(
            uniformLocation(ringProgram, "uPlanetCenter"),
            saturn.position.x.toFloat(),
            saturn.position.y.toFloat(),
            saturn.position.z.toFloat()
        )
        val tiltRad = Math.toRadians(saturn.axialTiltDeg)
        GLES30.glUniform3f(
            uniformLocation(ringProgram, "uRingNormal"),
            -sin(tiltRad).toFloat(), cos(tiltRad).toFloat(), 0f
        )
        GLES30.glUniform1f(
            uniformLocation(ringProgram, "uEquatorialRadius"),
            ringRadius
        )

        bindTexture(0, saturnRingTexture)
        GLES30.glUniform1i(
            uniformLocation(ringProgram, "uTexture"),
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
            uniformLocation(starProgram, "uVp"),
            1, false, viewProjection, 0
        )
        GLES30.glUniform1f(uniformLocation(starProgram, "uPointSize"), 2.0f)
        GLES30.glUniform4f(uniformLocation(starProgram, "uColor"), 0.82f, 0.88f, 1.0f, 0.72f)

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
            uniformLocation(starProgram, "uVp"),
            1, false, viewProjection, 0
        )
        GLES30.glUniform1f(uniformLocation(starProgram, "uPointSize"), if (selectedId == "ceres") 3.4f else 2.35f)
        GLES30.glUniform4f(uniformLocation(starProgram, "uColor"), 0.67f, 0.62f, 0.54f, 0.62f)

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, buffer)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, asteroidBeltOrbits.size)
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
        GLES30.glUniformMatrix4fv(uniformLocation(lineProgram, "uVp"), 1, false, viewProjection, 0)
        GLES30.glUniform3f(uniformLocation(lineProgram, "uOffset"), 0f, 0f, 0f)
        GLES30.glUniform4f(uniformLocation(lineProgram, "uColor"), 0.92f, 0.48f, 0.19f, opacity)
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
            "mercury", "ceres", "europa", "titan" -> when {
                altitudeKm > (if(body.id=="titan") 1400.0 else 650.0) -> "ORBIT"
                altitudeKm > (if(body.id=="ceres") 32.0 else 95.0) -> "CLOSE APPROACH"
                altitudeKm > (if(body.id=="ceres") 3.0 else 18.0) -> "LOW ORBIT"
                else -> "SURFACE SKIM"
            }
            "moon" -> when {
                altitudeKm > 900.0 -> "ORBIT"
                altitudeKm > 120.0 -> "CLOSE APPROACH"
                altitudeKm > 15.0 -> "LOW ORBIT"
                else -> "SURFACE SKIM"
            }
            "venus" -> when {
                altitudeKm > 4000.0 -> "ORBIT"
                altitudeKm > 300.0 -> "CLOSE APPROACH"
                else -> "CLOUD TOPS"
            }
            else -> "ORBIT"
        }

        latestApproach = ApproachSnapshot(
            bodyId = body.id,
            altitudeKm = altitudeKm,
            stage = stage,
            closeLod = when (body.id) {
                "mars" -> altitudeKm < 2500.0
                "moon" -> altitudeKm < 1500.0
                "venus" -> altitudeKm < 2000.0
                "mercury", "ceres", "europa", "titan" -> altitudeKm < 1500.0
                else -> false
            }
        )
    }

    private fun drawPlanetAtmosphere(body: CelestialBody) {
        if (body.id !in setOf("earth", "venus", "mars")) return
        val altitude = if (latestApproach.bodyId == body.id) latestApproach.altitudeKm else 1e6
        val opacity = when (body.id) {
            "earth" -> if (altitude < 150.0) 0.60f else 0.43f
            "venus" -> 0.56f
            else -> if (altitude < 300.0) 0.30f else 0.16f
        }

        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDepthMask(false)

        buildBodyModel(body, when (body.id) {
            "earth" -> 1.038f
            "venus" -> 1.045f
            else -> 1.022f
        }, 0f)
        GLES30.glUseProgram(planetProgram)
        bindPlanetCommon(body)
        GLES30.glUniform4f(
            uniformLocation(planetProgram, "uColor"),
            when (body.id) { "earth" -> .28f; "venus" -> .99f; else -> .92f },
            when (body.id) { "earth" -> .57f; "venus" -> .73f; else -> .38f },
            when (body.id) { "earth" -> 1.0f; "venus" -> .48f; else -> .18f }, 1f
        )
        bindTexture(0, 0)
        GLES30.glUniform1i(uniformLocation(planetProgram, "uTexture"), 0)
        GLES30.glUniform1i(uniformLocation(planetProgram, "uUseTexture"), 0)
        bindTexture(1, 0)
        GLES30.glUniform1i(uniformLocation(planetProgram, "uNightTexture"), 1)
        GLES30.glUniform1i(uniformLocation(planetProgram, "uUseNight"), 0)
        bindTexture(2, 0)
        GLES30.glUniform1i(uniformLocation(planetProgram, "uNormalTexture"), 2)
        GLES30.glUniform1i(uniformLocation(planetProgram, "uUseNormal"), 0)
        GLES30.glUniform1i(uniformLocation(planetProgram, "uMode"), 3)
        GLES30.glUniform1f(uniformLocation(planetProgram, "uOpacity"), opacity)
        drawSphereGeometry()

        GLES30.glDepthMask(true)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
    }

    private fun loadPlanetTextures() {
        val ids = listOf(
            "europa", "titan",
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

    private val cachedUniforms = HashMap<Int, MutableMap<String, Int>>(12)

    /**
     * Rendering Mars/Moon and the full solar system made hundreds of driver
     * uniform-location lookups per frame. Cache only the locations, NEVER
     * the uniform values. A GLSurfaceView context recreation clears the
     * cache before programs are recompiled in onSurfaceCreated.
     */
    private fun uniformLocation(program: Int, name: String): Int {
        val uniforms = cachedUniforms.getOrPut(program) { HashMap(30) }
        return uniforms.getOrPut(name) { GLES30.glGetUniformLocation(program, name) }
    }

    private fun loadTextureResource(name: String, repeatX: Boolean = true): Int {
        val resId = context.resources.getIdentifier(
            name,
            "drawable",
            context.packageName
        )
        if (resId == 0) return 0

        // GLES3 hardware need only support 2048px textures. Decode the
        // required mip BEFORE uploading, and cap memory on low-RAM phones.
        // No upscale: 3072px Hubble is retained on capable GPUs.
        val bounds = BitmapFactory.Options().apply {
            inScaled = false
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeResource(context.resources, resId, bounds)
        val hardwareMaximum = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, hardwareMaximum, 0)
        val lowRam = (context.getSystemService(Context.ACTIVITY_SERVICE)
            as? ActivityManager)?.isLowRamDevice ?: false
        val sample = PlanetTextureSizing.inSampleSize(
            bounds.outWidth, bounds.outHeight, hardwareMaximum[0], lowRam
        )
        val bitmap = BitmapFactory.decodeResource(
            context.resources, resId,
            BitmapFactory.Options().apply {
                inScaled = false
                inSampleSize = sample
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
uniform float uPolarRatio;

out vec3 vNormal;
out vec3 vWorld;
out vec2 vUv;

void main() {
    vec4 world = uModel * vec4(aPosition, 1.0);
    vWorld = world.xyz;
    // Ellipsoid normal = inverse-transpose of nonuniform model scale.
    // Scale by 1/(b/a)^2 before uModel so its embedded y flattening
    // leaves 1/(b/a) relative to the equatorial components.
    float ratio2 = max(uPolarRatio * uPolarRatio, 0.25);
    vNormal = normalize(mat3(uModel) *
                        vec3(aNormal.x, aNormal.y / ratio2, aNormal.z));
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
uniform int uPlanetKind;
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
        ? texture(uTexture, vUv)
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
        // Clouds should not emit full daylight brightness on the night side.
        float sunlight = max(dot(normalize(vNormal), normalize(-vWorld)), 0.0);
        fragColor = vec4(texel.rgb * (0.06 + 0.94 * sunlight), cloud * uOpacity);
        return;
    }

    vec3 N = normalize(vNormal);
    vec3 L = normalize(-vWorld);
    vec3 V = normalize(uCamera - vWorld);

    if (uMode == 3) {
        // Single-scattering-inspired limb glow, strongest toward the Sun.
        // An approximation, not full atmospheric radiative transfer.
        float mu = dot(N, L);
        float limb = pow(1.0 - clamp(dot(N, V), 0.0, 1.0), 3.0);
        float daylight = smoothstep(-0.42, 0.26, mu);
        float horizon = pow(max(1.0 - abs(mu), 0.0), 3.0);
        vec3 sunset = mix(uColor.rgb * vec3(1.0, 0.40, 0.12), uColor.rgb,
            smoothstep(-0.12, 0.30, mu));
        vec3 atmosphere = sunset * (0.12 + 0.88 * daylight);
        float alpha = limb * (0.32 + 0.68 * daylight + 0.16 * horizon) * uOpacity;
        if (alpha < 0.003) discard;
        fragColor = vec4(atmosphere, clamp(alpha, 0.0, 0.85));
        return;
    }

    if (uMode == 2) {
        // Venus' opaque upper cloud deck obeys the day/night terminator.
        float sunlight = max(dot(N, L), 0.0);
        fragColor = vec4(texel.rgb * (0.06 + 0.94 * sunlight), uOpacity);
        return;
    }

    if (uUseNormal == 1) {
        vec3 mapped = texture(uNormalTexture, vUv).xyz * 2.0 - 1.0;

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
    if (uPlanetKind == 1 && uUseTexture == 1) {
        // A first-pass ocean mask using blue-dominant day-map pixels.
        float ocean = smoothstep(0.015, 0.15, texel.b - texel.r) *
            smoothstep(0.005, 0.095, texel.b - texel.g);
        float reflection = pow(max(dot(reflect(-L, N), V), 0.0), 110.0);
        color += vec3(0.84, 0.94, 1.0) * reflection * ocean * ndl * 0.85;
    }

    if (uUseNight == 1 && uEmissive < 0.5) {
        vec3 night = texture(uNightTexture, vUv).rgb;
        float darkness = pow(1.0 - ndl, 1.7);
        color += night * darkness * 0.58;
    }

    if (uPlanetKind == 2) {
        // Thick gas-giant clouds soften toward the limb rather than forming
        // a bright hard artificial outline on the night side.
        color *= mix(0.76, 1.0, pow(facing, 0.45));
    } else {
        color += texel.rgb * rim * 0.08 * (1.0 - uEmissive);
    }
    fragColor = vec4(color, texel.a * uColor.a);
}
"""

        private const val MARS_SKY_VERTEX_SHADER = """#version 300 es
out vec2 vUv;
void main() {
    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    vUv = p;
    gl_Position = vec4(p * 2.0 - 1.0, 1.0, 1.0);
}
"""
        private const val MARS_SKY_FRAGMENT_SHADER = """#version 300 es
precision highp float;
in vec2 vUv;
uniform float uPitch;
uniform float uYaw;
uniform float uAspect;
uniform int uSkyWorld; // 0 Mars, 1 Titan, 2 Europa, 3 Moon
uniform vec3 uEarthDirection; // local +X east, +Y up, +Z north at Tycho
uniform sampler2D uParentTexture;
uniform int uHasParentTexture;
out vec4 fragColor;

const float TAN_HALF_FOV = 0.6619; // tan(67 deg / 2)
const float JUPITER_ANGULAR_RADIUS = 0.1061; // asin(71492km / Europa orbit 671100km)
const float JUPITER_POLAR_RATIO = 0.9351; // polar / equatorial radii
const float EARTH_ANGULAR_RADIUS = 0.01659; // asin(6378.137km / 384400km)

vec3 viewRay(vec2 uv) {
    vec2 ndc = uv * 2.0 - 1.0;
    vec3 forward = vec3(sin(uYaw)*cos(uPitch), sin(uPitch), -cos(uYaw)*cos(uPitch));
    vec3 right = vec3(cos(uYaw), 0.0, sin(uYaw));
    vec3 up = vec3(-sin(uYaw)*sin(uPitch), cos(uPitch), cos(uYaw)*sin(uPitch));
    return normalize(forward + right * ndc.x * uAspect * TAN_HALF_FOV +
                     up * ndc.y * TAN_HALF_FOV);
}

void main() {
    vec3 ray = viewRay(vUv);
    float elev = max(-0.1, ray.y);
    vec3 sky = vec3(0.002, 0.002, 0.003);

    if (uSkyWorld == 0) {
        // A computationally cheap single-scattering approximation, built
        // from coherent horizon optical depth and a forward Mie aerosol
        // phase function. Its anisotropic halo tracks actual camera yaw.
        // No decorative cloud bands or terrestrial blue horizon.
        vec3 sunDirection = normalize(vec3(-.45, .85, .35));
        float cosTheta = clamp(dot(ray,sunDirection),-1.0,1.0);
        float g = .72;
        float mie = (1.0-g*g) /
            pow(max(1.0+g*g-2.0*g*cosTheta,.0025),1.5);
        float airMass = exp(-5.5*max(elev,0.0));
        float zenith = smoothstep(-.035,.75,elev);
        vec3 horizon = vec3(.465,.282,.199);
        vec3 upperSky = vec3(.164,.164,.186);
        sky = mix(horizon,upperSky,zenith);
        sky += vec3(.035,.019,.010)*airMass;
        sky += vec3(.94,.64,.40)*min(mie*.007, .40);
        // Even a small 0.18-degree Martian Sun should not be a
        // screen-space white bloom pasted into a brown gradient.
        float sunDisk = smoothstep(.999984,.999996,cosTheta);
        sky += vec3(.90,.82,.68)*sunDisk*.44;
    } else if (uSkyWorld == 1) {
        // The Titan Huygens atmosphere is optically thick. Aerosol Mie
        // scattering is strongly forward-peaked, but the direct solar
        // disk is largely hidden at the surface. No stars or Saturn.
        vec3 sunDirection = normalize(vec3(-.32,.58,.31));
        float cosTheta = clamp(dot(ray,sunDirection),-1.0,1.0);
        float g = .83;
        float mie = (1.0-g*g) /
            pow(max(1.0+g*g-2.0*g*cosTheta,.008),1.5);
        float airMass = exp(-2.8*max(elev,0.0));
        vec3 upperHaze = vec3(.278,.170,.086);
        vec3 horizonHaze = vec3(.48,.285,.125);
        sky = mix(upperHaze,horizonHaze,airMass);
        sky += vec3(.065,.045,.023)*min(mie*.009,.27);
    } else if (uSkyWorld == 2 && uHasParentTexture == 1 && ray.y > 0.0) {
        // Europa's near-side Jupiter view: physically small, oblate and
        // directly illuminated by the same Sun used on the ice terrain.
        // Camera-space fixed direction is illustrative: no ephemeris claim.
        vec3 towardJupiter = normalize(vec3(.88, .36, -.30));
        vec3 diskRight = normalize(cross(vec3(0.0, 1.0, 0.0), towardJupiter));
        vec3 diskUp = normalize(cross(towardJupiter, diskRight));
        vec2 transverse = vec2(dot(ray, diskRight), dot(ray, diskUp)) /
            sin(JUPITER_ANGULAR_RADIUS);
        // Jupiter rotates fast enough to bulge at the equator: its polar
        // radius is ~93.5% of its equatorial radius. A perfect circle
        // looks like a flat coin and is measurably incorrect.
        vec2 p = vec2(transverse.x, transverse.y / JUPITER_POLAR_RATIO);
        float radiusSquared = dot(p, p);
        if (dot(ray, towardJupiter) > 0.0 && radiusSquared < 1.045) {
            float projectedRadius = sqrt(radiusSquared);
            float rim = fwidth(projectedRadius) * 1.35;
            float edgeAlpha = 1.0 - smoothstep(1.0 - rim, 1.0 + rim, projectedRadius);
            if (edgeAlpha > 0.0) {
                float depth = sqrt(max(1.0 - radiusSquared, 0.0));
                // Band rotation is orbital presentation, not a screenshot
                // pasted in a circle. Keep equirectangular band continuity.
                float longitude = atan(p.x, depth);
                float latitude = asin(clamp(p.y, -1.0, 1.0));
                vec2 uv = vec2(fract(.50 + longitude / 6.2831853),
                               .50 - latitude / 3.14159265);
                vec3 clouds = texture(uParentTexture, uv).rgb;

                // A sphere needs *solar lighting and a terminator*, not
                // a uniformly bright photograph. Camera-facing outward
                // normal is the opposite of the ray toward Jupiter.
                vec3 normalWorld = normalize(-towardJupiter * depth +
                                            diskRight * p.x + diskUp * p.y);
                vec3 sunlight = normalize(vec3(-.74, .38, .31));
                float incident = dot(normalWorld, sunlight);
                float lit = smoothstep(-.025, .070, incident);
                float day = .12 + .88 * max(incident, 0.0);
                float phase = mix(.018, day, lit);

                // Jovian clouds scatter sunlight strongly but show a
                // noticeable curved limb: attenuate grazing sight lines.
                float limb = .56 + .44 * pow(depth, .39);
                vec3 radiance = clouds * phase * limb;
                // No air or false blue glow around the disk on Europa.
                sky = mix(sky, radiance, edgeAlpha);
            }
        }
    } else if (uSkyWorld == 3 && uHasParentTexture == 1 && ray.y > 0.0) {
        // Near-side Moon (Tycho): Earth is approximately 1.9 degrees
        // across, at ~45 degrees elevation toward north-northeast. The
        // actual vector is derived from Tycho's -43.31°,348.64°E site
        // and the mean sub-Earth point (no time-dependent libration).
        vec3 towardEarth = normalize(uEarthDirection);
        vec3 east = normalize(cross(vec3(0.0, 1.0, 0.0), towardEarth));
        vec3 north = normalize(cross(towardEarth, east));
        vec2 p = vec2(dot(ray, east), dot(ray, north)) /
            sin(EARTH_ANGULAR_RADIUS);
        float r2 = dot(p, p);
        if (dot(ray, towardEarth) > 0.0 && r2 < 1.045) {
            float distanceFromCenter = sqrt(r2);
            float fringe = max(fwidth(distanceFromCenter) * 1.4, .002);
            float coverage = 1.0 - smoothstep(
                1.0 - fringe, 1.0 + fringe, distanceFromCenter
            );
            if (coverage > 0.0) {
                float depth = sqrt(max(1.0 - r2, 0.0));
                float longitude = atan(p.x, depth);
                float latitude = asin(clamp(p.y, -1.0, 1.0));
                vec2 uv = vec2(fract(.5 + longitude / 6.2831853),
                               .5 - latitude / 3.14159265);
                vec3 earth = texture(uParentTexture, uv).rgb;
                // Earth and Moon receive sunlight from the same physical
                // direction. A lit, clouded globe has a phase and shaded
                // limb; avoid a pasted, uniformly illuminated flat disk.
                vec3 normalWorld = normalize(
                    -towardEarth * depth + east * p.x + north * p.y
                );
                float incident = dot(
                    normalWorld, normalize(vec3(-.74, .38, .31))
                );
                float daylight = smoothstep(-.025, .07, incident);
                float phase = mix(.018, .19 + .81 * max(incident, 0.0), daylight);
                float limb = .63 + .37 * pow(depth, .42);
                sky = mix(sky, earth * phase * limb, coverage);
            }
        }
    }

    // Low-amplitude stable ordered-noise dither for 8-bit Android panels.
    float noise = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) *
        43758.5453) - .5;
    fragColor = vec4(clamp(sky + vec3(noise / 255.0), 0.0, 1.0), 1.0);
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
uniform mat4 uModel;
out vec2 vUv;
out highp vec3 vWorld;

void main() {
    vUv = aUv;
    vWorld = (uModel * vec4(aPosition, 1.0)).xyz;
    gl_Position = uMvp * vec4(aPosition, 1.0);
}
"""

        private const val RING_FRAGMENT_SHADER = """#version 300 es
precision highp float;

in vec2 vUv;
in highp vec3 vWorld;
uniform sampler2D uTexture;
uniform vec3 uPlanetCenter;
uniform vec3 uRingNormal;
uniform float uEquatorialRadius;
out vec4 fragColor;

void main() {
    vec4 t = texture(uTexture, vUv);
    float band = max(t.r, max(t.g, t.b));
    float alpha = t.a * band * 0.92;
    if (alpha < 0.02) discard;

    vec3 toSun = normalize(-uPlanetCenter);
    vec3 location = vWorld - uPlanetCenter;
    float sunHeight = abs(dot(normalize(uRingNormal), toSun));
    // Rings scatter sunlight across both their sides, but their radiance
    // drops when illuminated nearly edge-on at ring-plane crossings.
    float illumination = 0.20 + 0.80 * sqrt(max(sunHeight, 0.0));

    // Planet shadow in the ring plane. The ray from the ring particle toward
    // the Sun must pass through Saturn before entering sunlight.
    float behindPlanet = dot(location, toSun);
    vec3 tangent = location - behindPlanet * toSun;
    float radialSunDistance = length(tangent) / max(uEquatorialRadius, .001);
    float blocking = (1.0 - smoothstep(.990, 1.025, radialSunDistance))
                     * (1.0 - smoothstep(-0.025, 0.025, behindPlanet));
    // Preserve a small indirect scattering floor inside the umbra.
    float shadow = mix(1.0, 0.11, blocking);
    fragColor = vec4(t.rgb * illumination * shadow, alpha);
}
"""
    }
}
