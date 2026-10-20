package studio.artistscene.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AxisNumericDraftTest {
    @Test fun acceptsTemporaryEditingStatesWithoutAllowingMalformedDecimals() {
        for (draft in listOf("", "-", ".", "-.", "12", "-12.45", ".3", "12,", "-.123")) {
            assertTrue(draft, AxisNumericDraft.accepts(draft))
        }
        for (draft in listOf("12-3", "1..2", "1,2.3", "12345678901234567", "NaN", " 2 ", "1e3")) {
            assertFalse(draft, AxisNumericDraft.accepts(draft))
        }
    }

    @Test fun parsesLocaleDecimalAndRejectsNonFiniteValues() {
        assertEquals(-0.25f, AxisNumericDraft.parse("-0,25")!!, 0f)
        assertEquals(12.5f, AxisNumericDraft.parse("12.5")!!, 0f)
        assertNull(AxisNumericDraft.parse("."))
        assertNull(AxisNumericDraft.parse("-"))
        assertNull(AxisNumericDraft.parse("1e500"))
        assertNull(AxisNumericDraft.parse("NaN"))
        assertNull(AxisNumericDraft.parse("Infinity"))
    }

    @Test fun maintainsPrecisionOnSmallPositiveAndNegativeValues() {
        assertEquals("0.01", AxisNumericDraft.display(0.01f))
        assertEquals("-0.005", AxisNumericDraft.display(-0.005f))
        assertEquals("1.25", AxisNumericDraft.display(1.25f))
        assertEquals("—", AxisNumericDraft.display(Float.NaN))
    }
}
