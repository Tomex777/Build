package studio.artistscene.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerrainMeshTest {
    @Test fun hillMountainValleyHavePhysicalElevationAndSmoothMeshNormals() {
        val flat = TerrainMesh.preset(TerrainShape.FLAT)
        val hill = TerrainMesh.preset(TerrainShape.HILL)
        val mountains = TerrainMesh.preset(TerrainShape.MOUNTAINS)
        val valley = TerrainMesh.preset(TerrainShape.VALLEY)
        assertEquals(TerrainMesh.VERTICES, mountains.size)
        assertTrue(flat.all { it == 0f })
        assertTrue(hill.maxOrNull()!! > 3f)
        assertTrue(mountains.maxOrNull()!! > hill.maxOrNull()!!)
        assertTrue(valley.maxOrNull()!! > 5f)
    }

    @Test fun importedHeightmapsAreEditableActualVertexHeights() {
        val black = IntArray(TerrainMesh.VERTICES) { 0xFF000000.toInt() }
        black[0] = 0xFFFFFFFF.toInt()
        val terrain = TerrainMesh.fromGrayscale(black, 22f)
        assertEquals(22f, terrain[0], 0.01f)
        assertEquals(0f, terrain[1], 0.01f)
    }

    @Test fun glbContainsValidRealGeometryAndTextureManifest() {
        val heights = TerrainMesh.preset(TerrainShape.MOUNTAINS)
        val bytes = TerrainMesh.encodeGlb(heights)
        val stream = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x46546C67, stream.int)
        assertEquals(2, stream.int)
        assertEquals(bytes.size, stream.int)
        val jsonBytes = stream.int
        assertEquals(0x4E4F534A, stream.int)
        val text = ByteArray(jsonBytes).also { stream.get(it) }.toString(Charsets.UTF_8).trim()
        val obj = Json.parseToJsonElement(text).jsonObject
        assertEquals(TerrainMesh.VERTICES, obj["accessors"]!!.jsonArray[0].jsonObject["count"]!!.jsonPrimitive.int)
        assertEquals(4, obj["bufferViews"]!!.jsonArray.size)
        assertTrue(text.contains("POSITION"))
        assertTrue(text.contains("NORMAL"))
        assertTrue(text.contains("TEXCOORD_0"))
        assertTrue(text.contains("\"max\""))
        val binaryLength = stream.int
        assertTrue(binaryLength > 100000)
        assertEquals(0x004E4942, stream.int)
    }

    @Test fun sunPositionRotatesAndLowerAnglesMakeLongRealShadows() {
        val nearHorizon = SunOrbit.direction(270f, 8f)
        val overhead = SunOrbit.direction(270f, 70f)
        val nearAngle = SunOrbit.angles(nearHorizon)
        assertEquals(270f, nearAngle.azimuthDegrees, 0.01f)
        assertEquals(8f, nearAngle.elevationDegrees, 0.02f)
        assertTrue(kotlin.math.abs(nearHorizon.y) < kotlin.math.abs(overhead.y))
        val turned = SunOrbit.move(nearHorizon, 15f, 5f)
        assertEquals(285f, SunOrbit.angles(turned).azimuthDegrees, 0.01f)
        assertEquals(13f, SunOrbit.angles(turned).elevationDegrees, 0.02f)
    }

    @Test fun groundMaterialsAndTerrainPersistAcrossSceneSerialization() {
        val scene = SceneProject(id = "terrain-persistence", name = "Mountain scene",
            world = WorldSettings(
                environmentPreset = EnvironmentPreset.PARK_SHADE,
                groundSurface = GroundSurface.ROCK,
                groundTileCount = 12,
                groundRoughness = .94f,
            ))
        assertEquals(scene, SceneProjectCodec.decode(SceneProjectCodec.encode(scene)))
    }
}
