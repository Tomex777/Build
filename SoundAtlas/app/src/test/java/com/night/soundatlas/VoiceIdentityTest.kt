package com.night.soundatlas

import org.junit.Assert.*
import org.junit.Test

/**
 * Worlds may both have emitter 1, but it need not be the same recording.
 * Equal numeric IDs alone must never be used to retain sampled voices.
 */
class VoiceIdentityTest {
    @Test fun switchingWorldsDoesNotReuseStaleFieldRecordings() {
        val cathedral = AtlasPresets.create(0)
        val forest = AtlasPresets.create(1)
        assertEquals(cathedral.sounds.first().id,forest.sounds.first().id)
        val before=cathedral.sounds.first()
        val after=forest.sounds.first()
        assertNotEquals(
            VoiceKey(before.id,before.kind,before.seed),
            VoiceKey(after.id,after.kind,after.seed)
        )
    }
    @Test fun movingTheSameEmitterRetainsItsVoiceHistory() {
        val sound=AtlasPresets.create(1).sounds.first()
        val key=VoiceKey(sound.id,sound.kind,sound.seed)
        sound.at=sound.at.moved(4f,-2f)
        assertEquals(key,VoiceKey(sound.id,sound.kind,sound.seed))
    }
}
