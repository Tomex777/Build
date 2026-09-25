package app.nami.compat.aniyomi

import kotlin.test.Test
import kotlin.test.assertEquals

class ExtensionSignerPolicyTest {

    @Test
    fun firstSeenSignedPackageIsPinned() {
        assertEquals(
            ExtensionSignerDecision.FIRST_SEEN_PINNED,
            ExtensionSignerPolicy.decide(
                pinnedFingerprints = emptySet(),
                currentFingerprints = setOf("abc"),
            ),
        )
    }

    @Test
    fun matchingSignerIsTrustedAcrossUpdates() {
        assertEquals(
            ExtensionSignerDecision.TRUSTED,
            ExtensionSignerPolicy.decide(
                pinnedFingerprints = setOf("abc"),
                currentFingerprints = setOf("abc"),
            ),
        )
    }

    @Test
    fun signerRotationHistoryCanStillMatchPinnedSigner() {
        assertEquals(
            ExtensionSignerDecision.TRUSTED,
            ExtensionSignerPolicy.decide(
                pinnedFingerprints = setOf("old"),
                currentFingerprints = setOf("new", "old"),
            ),
        )
    }

    @Test
    fun changedSignerForSamePackageIsRejected() {
        assertEquals(
            ExtensionSignerDecision.REJECTED,
            ExtensionSignerPolicy.decide(
                pinnedFingerprints = setOf("trusted"),
                currentFingerprints = setOf("different"),
            ),
        )
    }

    @Test
    fun unsignedPackageIsRejected() {
        assertEquals(
            ExtensionSignerDecision.UNSIGNED,
            ExtensionSignerPolicy.decide(
                pinnedFingerprints = emptySet(),
                currentFingerprints = emptySet(),
            ),
        )
    }
}
