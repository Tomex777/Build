package studio.artistscene.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.artistscene.core.SceneProjectSummary

class ProjectBrowserFilterTest {
    private val scenes = listOf(
        SceneProjectSummary("c", "Portrait study", 1, 40L),
        SceneProjectSummary("a", "Night City", 4, 200L),
        SceneProjectSummary("b", "Night forest", 7, 200L),
        SceneProjectSummary("d", "Landscape", 0, 10L),
    )

    @Test fun sortsRecentFirstWithDeterministicTies() {
        assertEquals(listOf("a", "b", "c", "d"), filterRecentScenes(scenes, "").map { it.id })
    }

    @Test fun searchIgnoresCaseAndOutsideWhitespace() {
        assertEquals(listOf("a", "b"), filterRecentScenes(scenes, "  NIGHT ").map { it.id })
    }

    @Test fun missingNameDoesNotDeleteOrRewriteProjects() {
        assertTrue(filterRecentScenes(scenes, "no such scene").isEmpty())
        assertEquals(4, scenes.size)
        assertEquals("Portrait study", scenes.first().name)
    }
}
