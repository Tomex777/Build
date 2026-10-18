package com.night.soundatlas

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

data class V2(var x: Float, var y: Float) {
    fun distance(other: V2): Float = hypot(x - other.x, y - other.y)
    fun moved(dx: Float, dy: Float) = V2(x + dx, y + dy)
}

enum class Material(val title: String, val transmission: Float, val absorption: Float, val color: Int) {
    STONE("Stone", 0.13f, 0.12f, 0xFF8498AB.toInt()),
    WOOD("Wood", 0.29f, 0.28f, 0xFFBA936C.toInt()),
    GLASS("Glass", 0.41f, 0.12f, 0xFF79C3D0.toInt()),
    METAL("Metal", 0.12f, 0.08f, 0xFFB4C7CB.toInt()),
    CURTAIN("Curtain", 0.53f, 0.75f, 0xFFC6A7A3.toInt()),
    OPEN("Open passage", 1.0f, 0.0f, 0xFF65867B.toInt())
}

enum class SoundKind(val title: String, val family: String, val color: Int) {
    RAIN("Rainfall", "Weather", 0xFF7BAFDA.toInt()),
    THUNDER("Thunder", "Weather", 0xFFE6C7A1.toInt()),
    WIND("Wind", "Weather", 0xFF7AA9B1.toInt()),
    DRIP("Water drips", "Water", 0xFF90CAE1.toInt()),
    WATER("Flowing water", "Water", 0xFF5DA8BC.toInt()),
    BIRDS("Birdsong", "Wildlife", 0xFFB3BF7F.toInt()),
    LEAVES("Foliage", "Nature", 0xFF79A48D.toInt()),
    CRICKETS("Crickets", "Wildlife", 0xFFBCB67A.toInt()),
    BELL("Bell", "Objects", 0xFFD6B178.toInt()),
    HUM("Machinery", "Industrial", 0xFFB1A3CC.toInt()),
    METAL("Metal creaks", "Industrial", 0xFFC5A7A1.toInt()),
    STEPS("Footsteps", "Human", 0xFFBEAD91.toInt()),
    CROWD("Distant crowd", "Human", 0xFFB5A4B5.toInt()),
    TRAFFIC("Passing traffic", "Urban", 0xFF90A2A9.toInt())
}

data class Wall(var a: V2, var b: V2, var material: Material = Material.STONE)
data class Zone(
    val name: String, var x: Float, var y: Float, var w: Float, var h: Float,
    var decay: Float = 1.3f, var damping: Float = 0.4f
) {
    fun contains(v: V2) = v.x >= x && v.y >= y && v.x <= x + w && v.y <= y + h
}
data class Emitter(
    val id: Int, var kind: SoundKind, var at: V2,
    var gain: Float = 0.7f, var enabled: Boolean = true,
    var moving: Boolean = false, val seed: Int = id * 7919
)
data class World(
    var name: String, var width: Float, var height: Float, var listener: V2,
    var facing: Float = 0f,
    val walls: MutableList<Wall> = mutableListOf(),
    val zones: MutableList<Zone> = mutableListOf(),
    val sounds: MutableList<Emitter> = mutableListOf(),
    var wind: Float = 0.15f, var rain: Float = 0f,
    var seed: Int = 1357
) {
    fun bound(pos: V2) = V2(pos.x.coerceIn(0f, width), pos.y.coerceIn(0f, height))
    fun newId() = (sounds.maxOfOrNull { it.id } ?: 0) + 1
    fun copyForAudio(): World = copy(
        listener = listener.copy(),
        walls = walls.map { Wall(it.a.copy(), it.b.copy(), it.material) }.toMutableList(),
        zones = zones.map { it.copy() }.toMutableList(),
        sounds = sounds.map { it.copy(at = it.at.copy()) }.toMutableList()
    )
}

data class AcousticCoefficients(
    val directGain: Float, val lowPassHz: Float, val reverbSeconds: Float,
    val absorption: Float, val pan: Float, val distance: Float, val occlusions: Int
)
object AcousticSolver {
    private fun cross(a: V2, b: V2) = a.x * b.y - a.y * b.x
    fun intersects(a: V2, b: V2, c: V2, d: V2): Boolean {
        val ab = V2(b.x - a.x, b.y - a.y)
        val cd = V2(d.x - c.x, d.y - c.y)
        val determinant = cross(ab, cd)
        if (abs(determinant) < 0.00001f) return false
        val ac = V2(c.x - a.x, c.y - a.y)
        val t = cross(ac, cd) / determinant
        val u = cross(ac, ab) / determinant
        return t > 0.001f && t < 0.999f && u >= 0f && u <= 1f
    }
    fun evaluate(world: World, emitter: Emitter, at: V2 = emitter.at): AcousticCoefficients {
        val distance = at.distance(world.listener).coerceAtLeast(0.1f)
        var wallTransmission = 1f
        var occlusions = 0
        for (wall in world.walls) {
            if (intersects(world.listener, at, wall.a, wall.b)) {
                wallTransmission *= wall.material.transmission
                if (wall.material != Material.OPEN) occlusions++
            }
        }
        // Direction 0 looks north; angle uses world x,y. Position remains in meters.
        val azimuth = atan2(at.x - world.listener.x, -(at.y - world.listener.y)) - world.facing
        val pan = sin(azimuth).coerceIn(-1f, 1f)
        // Physically inspired, but intentionally approximate rather than a claim of measured simulation.
        val direct = (1f / (1f + 0.075f * distance + 0.009f * distance * distance)) *
            wallTransmission.coerceAtLeast(0.002f)
        val zone = world.zones.lastOrNull { it.contains(world.listener) }
        return AcousticCoefficients(
            directGain = direct,
            lowPassHz = (17000f / (1f + 0.024f * distance)) *
                0.32f.pow(occlusions).coerceAtLeast(0.01f),
            reverbSeconds = zone?.decay ?: 0.30f,
            absorption = zone?.damping ?: 0.72f,
            pan = pan, distance = distance, occlusions = occlusions
        )
    }
}

object AtlasPresets {
    val titles = listOf(
        "Storm Cathedral", "Ancient Forest", "Abandoned Hospital",
        "Underwater Station", "Old Railway Tunnel", "Rain Garden",
        "Village at Dawn", "Starship Meridian", "Ocean Cavern", "Desert Temple"
    )
    private fun rect(world: World, x: Float, y: Float, w: Float, h: Float, m: Material) {
        world.walls += Wall(V2(x,y),V2(x+w,y),m)
        world.walls += Wall(V2(x+w,y),V2(x+w,y+h),m)
        world.walls += Wall(V2(x+w,y+h),V2(x,y+h),m)
        world.walls += Wall(V2(x,y+h),V2(x,y),m)
    }
    fun create(index: Int): World {
        val world = World(titles[index.coerceIn(titles.indices)], 72f, 84f, V2(35f,44f))
        fun zone(name: String,x:Float,y:Float,w:Float,h:Float,decay:Float,damping:Float) {
            world.zones += Zone(name,x,y,w,h,decay,damping)
        }
        fun add(kind: SoundKind,x:Float,y:Float,gain:Float=0.7f,moving:Boolean=false) {
            val id=world.newId();world.sounds += Emitter(id,kind,V2(x,y),gain,true,moving,id*7919+world.seed)
        }
        when(index.coerceIn(titles.indices)) {
            0 -> {
                world.wind=.62f;world.rain=.9f
                zone("Nave",10f,11f,52f,62f,4.2f,.19f)
                // Actual openings, not overlapping wall-and-glass segments.
                // A 12-meter south door connects interior and exterior acoustics.
                world.walls += Wall(V2(10f,11f),V2(62f,11f),Material.STONE)
                world.walls += Wall(V2(10f,11f),V2(10f,31f),Material.STONE)
                world.walls += Wall(V2(10f,31f),V2(10f,46f),Material.GLASS)
                world.walls += Wall(V2(10f,46f),V2(10f,73f),Material.STONE)
                world.walls += Wall(V2(62f,11f),V2(62f,31f),Material.STONE)
                world.walls += Wall(V2(62f,31f),V2(62f,46f),Material.GLASS)
                world.walls += Wall(V2(62f,46f),V2(62f,73f),Material.STONE)
                world.walls += Wall(V2(10f,73f),V2(30f,73f),Material.STONE)
                world.walls += Wall(V2(42f,73f),V2(62f,73f),Material.STONE)
                add(SoundKind.RAIN,8f,42f,.86f);add(SoundKind.RAIN,65f,29f,.82f)
                add(SoundKind.THUNDER,3f,13f,.84f,true); add(SoundKind.DRIP,44f,24f,.62f)
                add(SoundKind.BELL,35f,13f,.41f); add(SoundKind.WIND,33f,82f,.48f)
                world.listener=V2(35f,55f)
            }
            1 -> {
                zone("Canopy",0f,0f,72f,84f,1.3f,.78f)
                add(SoundKind.BIRDS,15f,18f,.9f,true); add(SoundKind.BIRDS,64f,29f,.8f,true)
                add(SoundKind.WATER,10f,65f,.74f); add(SoundKind.CRICKETS,57f,75f,.62f)
                add(SoundKind.LEAVES,34f,32f,.92f);add(SoundKind.WIND,61f,17f,.45f)
                world.wind=.36f;world.listener=V2(34f,48f)
            }
            2 -> {
                zone("Ward",8f,10f,57f,62f,2.7f,.31f)
                rect(world,8f,10f,57f,62f,Material.STONE)
                world.walls += Wall(V2(9f,43f),V2(30f,43f),Material.WOOD)
                world.walls += Wall(V2(41f,43f),V2(65f,43f),Material.WOOD)
                world.walls += Wall(V2(38f,11f),V2(38f,29f),Material.GLASS)
                add(SoundKind.HUM,20f,18f,.65f);add(SoundKind.METAL,55f,61f,.57f)
                add(SoundKind.DRIP,14f,62f,.48f);add(SoundKind.STEPS,56f,28f,.28f,true)
                world.listener=V2(36f,52f)
            }
            3 -> {
                zone("Pressure hull",7f,19f,58f,48f,1.9f,.2f)
                rect(world,7f,19f,58f,48f,Material.METAL)
                world.walls += Wall(V2(34f,20f),V2(34f,37f),Material.METAL)
                world.walls += Wall(V2(34f,49f),V2(34f,67f),Material.METAL)
                add(SoundKind.HUM,13f,25f,.74f);add(SoundKind.METAL,59f,30f,.72f)
                add(SoundKind.WATER,65f,70f,.25f);add(SoundKind.DRIP,40f,59f,.52f)
                world.listener=V2(38f,42f)
            }
            4 -> {
                zone("Stone tunnel",9f,29f,55f,20f,3.6f,.09f)
                rect(world,9f,29f,55f,20f,Material.STONE)
                world.walls += Wall(V2(9f,39f),V2(27f,39f),Material.METAL)
                add(SoundKind.TRAFFIC,67f,39f,.72f,true);add(SoundKind.DRIP,26f,35f,.66f)
                add(SoundKind.METAL,53f,44f,.58f);add(SoundKind.WIND,6f,39f,.55f)
                world.listener=V2(19f,44f)
            }
            5 -> {
                zone("Garden",0f,0f,72f,84f,.75f,.7f)
                world.rain=.24f;world.wind=.15f
                rect(world,7f,22f,18f,18f,Material.WOOD)
                world.walls.removeAt(world.walls.lastIndex-1)
                add(SoundKind.WATER,46f,35f,.63f)
                add(SoundKind.BIRDS,60f,18f,.59f,true)
                add(SoundKind.LEAVES,13f,53f,.65f)
                add(SoundKind.RAIN,64f,72f,.3f);add(SoundKind.BELL,22f,28f,.27f)
                world.listener=V2(31f,42f)
            }
            6 -> {
                zone("Village square",0f,0f,72f,84f,.55f,.66f)
                zone("Blacksmith's forge",5f,10f,24f,23f,1.3f,.4f)
                // The southern forge entrance remains open to the square.
                world.walls+=Wall(V2(5f,10f),V2(29f,10f),Material.STONE)
                world.walls+=Wall(V2(5f,10f),V2(5f,33f),Material.STONE)
                world.walls+=Wall(V2(29f,10f),V2(29f,33f),Material.STONE)
                world.walls+=Wall(V2(5f,33f),V2(15f,33f),Material.WOOD)
                world.walls+=Wall(V2(22f,33f),V2(29f,33f),Material.WOOD)
                add(SoundKind.METAL,16f,20f,.77f)
                add(SoundKind.BELL,58f,11f,.66f)
                add(SoundKind.CROWD,50f,41f,.54f)
                add(SoundKind.STEPS,41f,59f,.52f,true)
                add(SoundKind.BIRDS,62f,72f,.62f,true)
                add(SoundKind.WIND,9f,67f,.30f)
                world.listener=V2(37f,46f)
            }
            7 -> {
                zone("Command deck",9f,11f,54f,61f,1.1f,.32f)
                zone("Engine bay",10f,49f,24f,22f,1.7f,.17f)
                rect(world,9f,11f,54f,61f,Material.METAL)
                // Structural bulkhead includes a six-meter passage.
                world.walls+=Wall(V2(34f,11f),V2(34f,35f),Material.METAL)
                world.walls+=Wall(V2(34f,41f),V2(34f,72f),Material.METAL)
                add(SoundKind.HUM,17f,60f,.83f)
                add(SoundKind.METAL,57f,29f,.57f)
                add(SoundKind.WIND,25f,31f,.46f)
                add(SoundKind.BELL,54f,16f,.23f)
                add(SoundKind.STEPS,41f,59f,.27f,true)
                world.listener=V2(47f,44f)
            }
            8 -> {
                zone("Sea cavern",5f,8f,61f,66f,4.8f,.16f)
                // Jagged stone ledge with clear opening into the lagoon.
                world.walls+=Wall(V2(7f,14f),V2(11f,61f),Material.STONE)
                world.walls+=Wall(V2(11f,61f),V2(26f,72f),Material.STONE)
                world.walls+=Wall(V2(38f,72f),V2(64f,65f),Material.STONE)
                world.walls+=Wall(V2(64f,65f),V2(61f,13f),Material.STONE)
                add(SoundKind.WATER,35f,75f,.94f)
                add(SoundKind.DRIP,18f,28f,.78f)
                add(SoundKind.DRIP,59f,46f,.6f)
                add(SoundKind.WIND,52f,67f,.53f)
                add(SoundKind.THUNDER,6f,5f,.24f,true)
                world.listener=V2(35f,33f)
            }
            else -> {
                zone("Ancient chamber",12f,15f,48f,51f,5.4f,.10f)
                world.walls+=Wall(V2(12f,15f),V2(60f,15f),Material.STONE)
                world.walls+=Wall(V2(12f,15f),V2(12f,66f),Material.STONE)
                world.walls+=Wall(V2(60f,15f),V2(60f,66f),Material.STONE)
                world.walls+=Wall(V2(12f,66f),V2(31f,66f),Material.STONE)
                world.walls+=Wall(V2(40f,66f),V2(60f,66f),Material.STONE)
                add(SoundKind.WIND,32f,80f,.85f)
                add(SoundKind.BELL,36f,19f,.33f)
                add(SoundKind.METAL,19f,44f,.25f)
                add(SoundKind.DRIP,55f,27f,.35f)
                add(SoundKind.STEPS,36f,51f,.3f,true)
                world.listener=V2(36f,58f)
                world.wind=.83f
            }
        }
        return world
    }
}

object AtlasCodec {
    const val VERSION = 1
    fun encode(world: World): String {
        val root=JSONObject()
        root.put("v",VERSION).put("n",world.name).put("d",JSONArray().put(world.width).put(world.height))
        root.put("l",JSONArray().put(world.listener.x).put(world.listener.y).put(world.facing))
        root.put("cl",JSONArray().put(world.wind).put(world.rain).put(world.seed))
        val walls=JSONArray()
        for (wall in world.walls) walls.put(JSONArray().put(wall.a.x).put(wall.a.y).put(wall.b.x)
            .put(wall.b.y).put(wall.material.ordinal))
        root.put("w",walls)
        val zones=JSONArray()
        for (zone in world.zones) zones.put(JSONArray().put(zone.name).put(zone.x).put(zone.y)
            .put(zone.w).put(zone.h).put(zone.decay).put(zone.damping))
        root.put("z",zones)
        val sounds=JSONArray()
        for (sound in world.sounds) sounds.put(JSONArray().put(sound.id).put(sound.kind.ordinal)
            .put(sound.at.x).put(sound.at.y).put(sound.gain).put(if(sound.enabled) 1 else 0)
            .put(if(sound.moving) 1 else 0).put(sound.seed))
        root.put("e",sounds)
        return root.toString()
    }
    fun decode(json: String): World {
        require(json.length <= 250_000) { "Scene exceeds 250 KB limit" }
        val root=JSONObject(json)
        require(root.getInt("v")==VERSION) { "Unsupported Sound Atlas scene version" }
        fun JSONArray.safe(index:Int, low:Float, high:Float):Float {
            val n=getDouble(index)
            require(n.isFinite() && n>=low && n<=high) { "Invalid world coordinate or parameter" }
            return n.toFloat()
        }
        val dims=root.getJSONArray("d")
        val width=dims.safe(0,8f,5000f);val height=dims.safe(1,8f,5000f)
        val listener=root.getJSONArray("l")
        val world=World(root.getString("n").take(64),width,height,
            V2(listener.safe(0,0f,width),listener.safe(1,0f,height)),
            listener.safe(2,-1000f,1000f))
        val climate=root.getJSONArray("cl")
        world.wind=climate.safe(0,0f,1f);world.rain=climate.safe(1,0f,1f)
        world.seed=climate.getInt(2)
        val walls=root.getJSONArray("w")
        require(walls.length() <= 1000) { "Too many walls" }
        repeat(walls.length()) {
            val a=walls.getJSONArray(it)
            val material=Material.entries.getOrNull(a.getInt(4)) ?: error("Unknown wall material")
            world.walls += Wall(V2(a.safe(0,0f,width),a.safe(1,0f,height)),
                V2(a.safe(2,0f,width),a.safe(3,0f,height)),material)
        }
        val zones=root.getJSONArray("z")
        require(zones.length() <= 60) { "Too many zones" }
        repeat(zones.length()) {
            val a=zones.getJSONArray(it)
            world.zones+=Zone(a.getString(0).take(60),a.safe(1,0f,width),
                a.safe(2,0f,height),a.safe(3,0.1f,width),a.safe(4,0.1f,height),
                a.safe(5,0.1f,15f),a.safe(6,0f,1f))
        }
        val emitters=root.getJSONArray("e")
        require(emitters.length() <= 400) { "Too many sounds" }
        val ids=mutableSetOf<Int>()
        repeat(emitters.length()) {
            val a=emitters.getJSONArray(it);val id=a.getInt(0)
            require(id>0 && ids.add(id)) { "Duplicate/invalid sound identifier" }
            val kind=SoundKind.entries.getOrNull(a.getInt(1)) ?: error("Unknown sound")
            world.sounds += Emitter(id,kind,V2(a.safe(2,0f,width),a.safe(3,0f,height)),
                a.safe(4,0f,1.5f),a.getInt(5)!=0,a.getInt(6)!=0,a.getInt(7))
        }
        return world
    }
}
