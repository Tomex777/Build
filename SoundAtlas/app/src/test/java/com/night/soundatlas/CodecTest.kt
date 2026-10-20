package com.night.soundatlas

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CodecTest {
    @Test fun climateAndGeometrySurvivePortableExport() {
        val a=AtlasPresets.create(0)
        a.rain=.73f
        a.wind=.27f
        a.storm=.91f
        a.sounds[0].moving=true
        val json=AtlasCodec.encode(a)
        assertFalse(json.contains("https://"))
        assertTrue("Only compact scene instructions are exported",json.length<32_000)
        val b=AtlasCodec.decode(json)
        assertEquals(a.name,b.name)
        assertEquals(a.walls.size,b.walls.size)
        assertEquals(a.zones.size,b.zones.size)
        assertEquals(a.sounds.size,b.sounds.size)
        assertEquals(.73f,b.rain,.001f)
        assertEquals(.27f,b.wind,.001f)
        assertEquals(.91f,b.storm,.001f)
        assertTrue(b.sounds[0].moving)
    }

    @Test fun existingVersionOneScenesRemainImportable() {
        val current=JSONObject(AtlasCodec.encode(AtlasPresets.create(1)))
        val oldClimate=current.getJSONArray("cl")
        val legacy=org.json.JSONArray().put(oldClimate.getDouble(0))
            .put(oldClimate.getDouble(1)).put(oldClimate.getInt(2))
        current.put("cl",legacy)
        val world=AtlasCodec.decode(current.toString())
        assertEquals(.45f,world.storm,.001f)
    }

    @Test fun oversizedOrInvalidWorldFilesAreRejected() {
        try { AtlasCodec.decode("x".repeat(250_001));fail("oversize accepted") }
        catch (_:IllegalArgumentException) {}
        val current=JSONObject(AtlasCodec.encode(AtlasPresets.create(0)))
        current.put("v",22)
        try { AtlasCodec.decode(current.toString());fail("unknown schema accepted") }
        catch (_:IllegalArgumentException) {}
        val broken=JSONObject(AtlasCodec.encode(AtlasPresets.create(0)))
        broken.getJSONArray("d").put(0,0)
        try { AtlasCodec.decode(broken.toString());fail("zero-sized world accepted") }
        catch (_:IllegalArgumentException) {}
    }

    @Test fun expandedSoundLibraryKeepsOldSceneOrdinalsStable() {
        assertEquals(0,SoundKind.RAIN.ordinal)
        assertEquals(13,SoundKind.TRAFFIC.ordinal)
        assertEquals(17,SoundKind.TRAIN.ordinal)
        assertEquals(22,SoundKind.entries.size)
        val scene=AtlasPresets.create(1)
        val emitter=Emitter(scene.newId(),SoundKind.WATERFALL,
            V2(7f,12f),.77f)
        scene.sounds+=emitter
        val restored=AtlasCodec.decode(AtlasCodec.encode(scene))
        val decoded=restored.sounds.last()
        assertEquals(SoundKind.WATERFALL,decoded.kind)
        assertEquals(.77f,decoded.gain,.001f)
    }

    @Test fun everyPackagedSceneFitsWithinCompactFileLimit() {
        for(index in AtlasPresets.titles.indices) {
            val w=AtlasPresets.create(index)
            assertTrue(AtlasCodec.encode(w).toByteArray(Charsets.UTF_8).size<250_000)
            assertEquals(w.name,AtlasCodec.decode(AtlasCodec.encode(w)).name)
        }
    }
}
