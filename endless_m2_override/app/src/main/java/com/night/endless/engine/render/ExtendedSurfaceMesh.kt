package com.night.endless.engine.render

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Exploratory terrain for Mercury, Ceres, Europa and Titan.
 * Important: these are physically *inspired*, deterministic illustrative
 * patches, NOT measured MESSENGER/Dawn/Galileo/Cassini DEMs. Never label
 * their relief as a surveyed landing site or pretend the camera is located
 * at a particular real crater. Existing Mars and Moon keep NASA MOLA/LOLA.
 * Render unit = 3 km; model is shared with collision and camera.
 */
internal object ExtendedSurfaceMesh {
    const val SIDE = 161
    const val STEP = 0.17f
    private val supported = setOf("mercury", "ceres", "europa", "titan")
    fun isSupported(id: String): Boolean = id in supported

    private fun crater(x: Double, z: Double, cx: Double, cz: Double,
                       radius: Double, depth: Double): Double {
        val d = sqrt((x-cx)*(x-cx)+(z-cz)*(z-cz))
        val bowl = -depth * exp(-d*d/(radius*radius*.55))
        val rim = depth * .34 * exp(-((d-radius)*(d-radius))/
            (radius*radius*.027))
        return bowl + rim
    }

    fun heightAt(id: String, x: Double, z: Double): Double {
        require(isSupported(id)) { "Unsupported solid-surface body: $id" }
        val broad = .045 * sin(x*.47+sin(z*.15)*.2) * cos(z*.39)
        return when (id) {
            "mercury" -> broad +
                crater(x,z,-2.9,1.8,2.0,.14) +
                crater(x,z,3.6,-1.4,1.1,.11) +
                crater(x,z,.8,4.1,.65,.042) +
                crater(x,z,-.7,-3.3,.49,.038) +
                .006 * sin(x*7.7+z*3.1) * cos(z*5.4-x*2.4)
            "ceres" -> .58*broad +
                crater(x,z,-3.2,2.3,2.5,.19) +
                crater(x,z,3.1,-2.2,1.0,.08) +
                .025 * sin(x*1.3+z*.3) * cos(z*.9)
            "europa" -> .38*broad +
                // Multi-family tectonic ice ridges and shallow troughs.
                // Hundreds-of-meters wide (mesh-resolvable) ridges are
                // different from the centimeter-scale material relief.
                // Pattern is illustrative, not Galileo DEM reconstruction.
                .021 * cos(x*1.7+z*.49) +
                .014 * cos(z*2.3-x*.32) +
                .035 * exp(-sin(x*1.32+z*.39).let { it*it } / .065) -
                .012 * exp(-sin(z*1.91-x*.34).let { it*it } / .034) +
                crater(x,z,2.4,-3.6,.65,.026)
            else -> .16*broad +
                // Titan's linear dunes are real, tens to >100 meters
                // high; their precise shapes at this site are unknown.
                // Subdued relief and haze keep dunes from looking like
                // artificial steep mountains or a cratered airless moon.
                .018 * sin(x*1.75+z*3.85+.43*sin(z*.21)) +
                .009 * sin(x*3.2+z*7.4) +
                .015 * sin(x*.75+z*.23)
        }
    }

    fun renderedHeightAt(id: String, x: Double, z: Double): Double =
        SurfaceTerrainSampling.renderedHeight(x,z,SIDE,STEP.toDouble()) { px,pz ->
            heightAt(id,px,pz)
        }

    fun build(id: String): MoonSurfaceMeshData {
        require(isSupported(id))
        val vertices = FloatArray(SIDE*SIDE*9)
        val indices = ShortArray((SIDE-1)*(SIDE-1)*6)
        val half = (SIDE-1)/2f
        var p=0
        for (zi in 0 until SIDE) for(xi in 0 until SIDE) {
            val x=(xi-half)*STEP; val z=(zi-half)*STEP
            val h=heightAt(id,x.toDouble(),z.toDouble()).toFloat()
            val delta=.015
            val slopeX=(heightAt(id,x+delta,z.toDouble())-
                heightAt(id,x-delta,z.toDouble())).toFloat()/(2*delta).toFloat()
            val slopeZ=(heightAt(id,x.toDouble(),z+delta)-
                heightAt(id,x.toDouble(),z-delta)).toFloat()/(2*delta).toFloat()
            val norm=1f/sqrt(1f+slopeX*slopeX+slopeZ*slopeZ)
            vertices[p++]=x;vertices[p++]=h;vertices[p++]=z
            vertices[p++]=-slopeX*norm;vertices[p++]=norm;vertices[p++]=-slopeZ*norm
            val tone=.43f+.06f*sin((x*2+z*1.1).toDouble()).toFloat()
            vertices[p++]=tone;vertices[p++]=tone;vertices[p++]=tone
        }
        var k=0
        for(z in 0 until SIDE-1)for(x in 0 until SIDE-1){
            val a=z*SIDE+x;val b=a+1;val c=a+SIDE;val d=c+1
            indices[k++]=a.toShort(); indices[k++]=c.toShort(); indices[k++]=b.toShort()
            indices[k++]=b.toShort(); indices[k++]=c.toShort(); indices[k++]=d.toShort()
        }
        return MoonSurfaceMeshData(vertices, indices, SIDE, STEP)
    }
}
