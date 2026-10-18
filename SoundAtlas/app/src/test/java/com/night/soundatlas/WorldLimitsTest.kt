package com.night.soundatlas

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WorldLimitsTest {
    @Test fun tenDefaultWorldsRespectMobileCpuBudget() {
        for(i in AtlasPresets.titles.indices){
            val w=AtlasPresets.create(i)
            assertTrue(w.sounds.size<=WorldLimits.MAX_SOURCES)
            assertTrue(w.walls.size<=WorldLimits.MAX_WALLS)
            assertTrue(w.zones.size<=WorldLimits.MAX_ZONES)
        }
    }
    @Test fun hostileSceneCannotInsertHundredsOfReflectionWalls() {
        val root=JSONObject(AtlasCodec.encode(AtlasPresets.create(0)))
        val walls=JSONArray()
        repeat(WorldLimits.MAX_WALLS+1) {
            walls.put(JSONArray().put(0).put(0).put(1).put(1).put(0))
        }
        root.put("w",walls)
        try {AtlasCodec.decode(root.toString());fail("Scene should be bounded")}
        catch(e:IllegalArgumentException){assertTrue(e.message!!.contains("walls"))}
    }
    @Test fun hostileSceneCannotImportHundredsOfActiveAudioTracks() {
        val root=JSONObject(AtlasCodec.encode(AtlasPresets.create(1)))
        val tracks=JSONArray()
        repeat(WorldLimits.MAX_SOURCES+1) {
            tracks.put(JSONArray().put(it+1).put(0).put(20).put(20)
                .put(1).put(1).put(0).put(it*31))
        }
        root.put("e",tracks)
        try {AtlasCodec.decode(root.toString());fail("Scene should be bounded")}
        catch(e:IllegalArgumentException){assertTrue(e.message!!.contains("sounds"))}
    }
}
