package com.night.soundatlas

import kotlin.math.*

/**
 * Finite-wall, single-bounce image-source early reflections.
 *
 * This is a geometrical-acoustics approximation, NOT full ray tracing or a
 * measured impulse response. Only unblocked specular paths are included.
 */
data class EarlyReflection(
    val wallIndex: Int,
    val distanceMeters: Float,
    val delayFrames: Int,
    val gain: Float,
    val pan: Float,
    val cutoffHz: Float
)

object EarlyReflections {
    private const val SPEED_OF_SOUND = 343f
    private const val MAX_DELAY_FRAMES = 16383
    private const val RATE = 44100
    private fun cross(a: V2, b: V2) = a.x*b.y-a.y*b.x
    private fun sub(a: V2, b: V2) = V2(a.x-b.x,a.y-b.y)
    private fun dot(a: V2, b: V2) = a.x*b.x+a.y*b.y
    private fun intersection(a: V2,b: V2,c: V2,d: V2): V2? {
        val ab=sub(b,a);val cd=sub(d,c)
        val div=cross(ab,cd)
        if(abs(div)<.00001f)return null
        val ac=sub(c,a)
        val t=cross(ac,cd)/div
        val u=cross(ac,ab)/div
        if(t<=.0001f||t>=.9999f||u<=.0001f||u>=.9999f)return null
        return V2(a.x+t*ab.x,a.y+t*ab.y)
    }
    private fun mirror(point: V2, a: V2, b: V2): V2? {
        val d=sub(b,a)
        val len=dot(d,d)
        if(len<.005f)return null
        val t=dot(sub(point,a),d)/len
        val p=V2(a.x+t*d.x,a.y+t*d.y)
        return V2(2f*p.x-point.x,2f*p.y-point.y)
    }
    fun solve(world:World,source:V2,maxCount:Int=4):List<EarlyReflection> {
        if(maxCount<=0)return emptyList()
        val reflections=ArrayList<EarlyReflection>(min(12,world.walls.size))
        for((index,wall) in world.walls.withIndex()) {
            if(wall.material==Material.OPEN)continue
            val mirrored=mirror(source,wall.a,wall.b)?:continue
            val bounce=intersection(world.listener,mirrored,wall.a,wall.b)?:continue
            val path=source.distance(bounce)+world.listener.distance(bounce)
            if(path<.1f)continue
            val delay=(path/SPEED_OF_SOUND*RATE).roundToInt()
            if(delay>=MAX_DELAY_FRAMES||delay<1)continue
            var blocked=false
            for((otherIndex,other) in world.walls.withIndex()) {
                if(otherIndex==index||other.material==Material.OPEN)continue
                if(AcousticSolver.intersects(source,bounce,other.a,other.b) ||
                    AcousticSolver.intersects(world.listener,bounce,other.a,other.b)){
                    blocked=true;break
                }
            }
            if(blocked)continue
            // Keep early reflections well below direct arrival. Softer material
            // is absorbent; smooth wall materials reflect more.
            val reflectivity=(1f-wall.material.absorption).coerceIn(0f,1f)
            val strength=reflectivity * .28f /
                (1f+.08f*path+.002f*path*path)
            if(strength<.0005f)continue
            val azimuth=atan2(bounce.x-world.listener.x,
                -(bounce.y-world.listener.y))-world.facing
            val pan=sin(azimuth).coerceIn(-1f,1f)
            val cutoff=when(wall.material) {
                Material.STONE->16000f
                Material.GLASS->13000f
                Material.METAL->18000f
                Material.WOOD->8500f
                Material.CURTAIN->4200f
                Material.OPEN->18000f
            }
            reflections+=EarlyReflection(index,path,delay,strength,pan,cutoff)
        }
        return reflections.sortedByDescending{it.gain}.take(maxCount)
    }
}
