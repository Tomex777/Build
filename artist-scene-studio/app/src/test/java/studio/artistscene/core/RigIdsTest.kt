package studio.artistscene.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RigIdsTest {
    @Test fun preservesExistingRigPaths() {
        val paths = listOf("root/hips", "root/hips/spine", "root/left-arm", "root/right-arm")
        assertEquals(paths, uniqueRigIds(paths))
    }

    @Test fun duplicateParentsAndChildrenKeepIndependentBindings() {
        val paths = listOf("root/arm", "root/arm/hand", "root/arm", "root/arm/hand")
        val ids = uniqueRigIds(paths)
        assertEquals(listOf("root/arm", "root/arm/hand", "root/arm~2", "root/arm/hand~2"), ids)
        val parents = listOf<Int?>(null, 0, null, 2)
        assertEquals(listOf(null, "root/arm", null, "root/arm~2"), parents.map { it?.let(ids::get) })
        assertEquals(ids, uniqueRigIds(paths))
    }

    @Test fun generatedSuffixDoesNotStealAnExistingId() {
        assertEquals(listOf("bone", "bone~3", "bone~2"), uniqueRigIds(listOf("bone", "bone", "bone~2")))
    }
}
