package studio.artistscene.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import studio.artistscene.core.GeneratedTerrainReader
import studio.artistscene.core.GroundSurface
import studio.artistscene.core.TerrainBrush
import studio.artistscene.core.TerrainMesh
import studio.artistscene.core.TerrainShape
import studio.artistscene.core.TerrainStroke

/** Runs against actual Android app-scoped model storage, not fake in-memory files. */
@RunWith(AndroidJUnit4::class)
class TerrainAssetRevisionInstrumentedTest {
    @Test fun sculptUndoFilesReopenAndRealMaterialSwapSurviveNewAssetReader() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assets = TerrainAssets(context)
        val createdPaths = mutableListOf<String>()
        try {
            val original = assets.preset(TerrainShape.FLAT)
            createdPaths.add(requireNotNull(original.asset).relativePath)
            val base = assets.heights(original)
            assertTrue(base.all { it == 0f })

            val next = assets.sculpt(original, listOf(
                TerrainStroke(0f, 0f, 4f, TerrainBrush.RAISE, 3f),
                TerrainStroke(1f, 0f, 2f, TerrainBrush.SMOOTH, 0.5f),
            ))
            createdPaths.add(requireNotNull(next.asset).relativePath)
            assertEquals(original.id, next.id)
            assertEquals(original.transform, next.transform)
            assertNotEquals(original.asset!!.relativePath, next.asset!!.relativePath)
            assertTrue(File(context.filesDir, original.asset.relativePath).isFile)
            assertTrue(File(context.filesDir, next.asset.relativePath).isFile)
            assertTrue(assets.heights(next).maxOrNull()!! > 0f)
            assertArrayEquals(base, TerrainAssets(context).heights(original), 0.0001f)

            val rock = assets.retexture(next, GroundSurface.ROCK)
            createdPaths.add(requireNotNull(rock.asset).relativePath)
            assertArrayEquals(
                assets.heights(next), TerrainAssets(context).heights(rock), 0.0001f,
            )
            val rockFile = File(context.filesDir, rock.asset!!.relativePath)
            val read = GeneratedTerrainReader.decode(rockFile.readBytes())
            val expected = context.assets.open("world/rock_ground_diff_1k.png").use {
                it.readBytes()
            }
            assertArrayEquals(expected, read.embeddedPng)
            assertEquals(GroundSurface.ROCK.name, rock.metadata["terrainSurface"])
            assertFalse(rock.asset.relativePath == next.asset.relativePath)
            assertEquals(TerrainMesh.VERTICES, read.heights.size)
        } finally {
            createdPaths.forEach { path -> File(context.filesDir, path).delete() }
        }
    }
}
