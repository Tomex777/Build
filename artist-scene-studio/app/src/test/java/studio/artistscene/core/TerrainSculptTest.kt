package studio.artistscene.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerrainSculptTest {
    @Test fun raiseAndLowerModifyOnlyTheLocalBrushFootprint() {
        val source = TerrainMesh.preset(TerrainShape.FLAT)
        val raised = TerrainSculpt.apply(source, TerrainStroke(
            0f, 0f, 3f, TerrainBrush.RAISE, 2f))
        val mid = (TerrainMesh.CELLS / 2) * (TerrainMesh.CELLS + 1) + TerrainMesh.CELLS / 2
        assertEquals(0f, source[mid], 0f)
        assertEquals(2f, raised[mid], 0.0001f)
        assertEquals(0f, raised[0], 0f)
        val lowered = TerrainSculpt.apply(raised, TerrainStroke(
            0f, 0f, 3f, TerrainBrush.LOWER, 1.5f))
        assertEquals(0.5f, lowered[mid], 0.0001f)
        assertEquals(0f, raised[0], 0f)
    }

    @Test fun smoothPreservesPlaneAndRelaxesPeaksWithoutMutation() {
        val source = TerrainMesh.preset(TerrainShape.FLAT)
        val mid = (TerrainMesh.CELLS / 2) * (TerrainMesh.CELLS + 1) + TerrainMesh.CELLS / 2
        source[mid] = 20f
        val smoothed = TerrainSculpt.apply(source, TerrainStroke(
            0f, 0f, 2f, TerrainBrush.SMOOTH, 1f))
        assertTrue(smoothed[mid] in 0f..20f)
        assertTrue(smoothed[mid] < source[mid])
        assertTrue(smoothed[mid - 1] > 0f)
        assertEquals(20f, source[mid], 0f)
        assertEquals(0f, smoothed[0], 0f)
    }

    @Test fun flattenChangesOnlyBrushAreaAndRespectsTarget() {
        val source = FloatArray(TerrainMesh.VERTICES) { 8f }
        val changed = TerrainSculpt.apply(source, TerrainStroke(
            0f, 0f, 2f, TerrainBrush.FLATTEN, 1f, 3f))
        val mid = (TerrainMesh.CELLS / 2) * (TerrainMesh.CELLS + 1) + TerrainMesh.CELLS / 2
        assertEquals(3f, changed[mid], 0.0001f)
        assertEquals(8f, changed[0], 0f)
        assertTrue(source.all { it == 8f })
    }

    @Test fun oldGeneratedTerrainGlbRecoversSculptableHeightsWithTexture() {
        val source = TerrainMesh.preset(TerrainShape.MOUNTAINS)
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 13, 10, 26, 10, 0, 1)
        val oldGlb = TerrainMesh.encodeGlb(source, png)
        val oldData = GeneratedTerrainReader.decode(oldGlb)
        assertArrayEquals(source, oldData.heights, 0.0001f)
        assertArrayEquals(png, oldData.embeddedPng)
        val edited = TerrainSculpt.apply(oldData.heights, TerrainStroke(
            -3f, -4f, 5f, TerrainBrush.RAISE, 2f))
        val revised = GeneratedTerrainReader.decode(TerrainMesh.encodeGlb(edited, oldData.embeddedPng))
        assertArrayEquals(edited, revised.heights, 0.0001f)
        assertArrayEquals(png, revised.embeddedPng)
        assertFalse(source.contentEquals(revised.heights))
    }

    @Test fun rejectsNonGeneratedOrDamagedMeshesAndInvalidBrush() {
        val heights = TerrainMesh.preset(TerrainShape.HILL)
        val bytes = TerrainMesh.encodeGlb(heights)
        val invalid = bytes.copyOf()
        ByteBuffer.wrap(invalid).order(ByteOrder.LITTLE_ENDIAN).putInt(8, invalid.size + 4)
        assertFails { GeneratedTerrainReader.decode(invalid) }
        assertFails { TerrainSculpt.apply(heights, TerrainStroke(
            Float.NaN, 0f, 2f, TerrainBrush.RAISE, 1f)) }
        assertFails { TerrainSculpt.apply(heights, TerrainStroke(
            0f, 0f, -1f, TerrainBrush.RAISE, 1f)) }
    }

    @Test fun outOfRangeBrushIsSafeAndTerrainRemainsWithinBounds() {
        val source = TerrainMesh.preset(TerrainShape.FLAT)
        val untouched = TerrainSculpt.apply(source, TerrainStroke(
            100f, 100f, 2f, TerrainBrush.RAISE, 2f))
        assertTrue(source.contentEquals(untouched))
        assertFalse(source === untouched)
        val raised = TerrainSculpt.apply(FloatArray(TerrainMesh.VERTICES) { 199f },
            TerrainStroke(0f, 0f, 4f, TerrainBrush.RAISE, 8f))
        assertTrue(raised.all { it <= 200f })
    }

    private inline fun assertFails(action: () -> Unit) {
        var failed = false
        try { action() } catch (_: IllegalArgumentException) { failed = true }
        assertTrue("Expected invalid terrain data to be rejected", failed)
    }
}
