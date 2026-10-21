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

    private fun hash2(x: Int, z: Int): Double {
        var h = (x * 374761393) xor (z * 668265263)
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)).toLong() and 0xffffffffL).toDouble() / 4294967296.0
    }
    private fun valueNoise(x: Double, z: Double): Double {
        val ix = kotlin.math.floor(x).toInt()
        val iz = kotlin.math.floor(z).toInt()
        val fx = x - kotlin.math.floor(x)
        val fz = z - kotlin.math.floor(z)
        val u = fx*fx*(3.0-2.0*fx)
        val v = fz*fz*(3.0-2.0*fz)
        return (1-v)*((1-u)*hash2(ix,iz)+u*hash2(ix+1,iz)) +
               v*((1-u)*hash2(ix,iz+1)+u*hash2(ix+1,iz+1))
    }
    private fun fractal(x: Double, z: Double): Double =
        (valueNoise(x,z)-.5)*.58 + (valueNoise(x*2.1+13.7,z*2.1-2.4)-.5)*.27 +
        (valueNoise(x*4.23-11.6,z*4.23+6.4)-.5)*.15

    /** Near-field features: modelled, not surveyed Galileo/Cassini geometry. */
    fun nearReliefAt(id: String, x: Double, z: Double): Double {
        return when(id) {
            "europa" -> {
                val warp = (valueNoise(x*55.0,z*55.0)-.5)*.018
                val fault = kotlin.math.abs(sin((x+warp)*97.0+(z-warp)*30.0))
                exp(-fault*fault/.006)*.00014 +
                    fractal(x*200.0,z*200.0)*.000042
            }
            "titan" -> {
                val warp=(valueNoise(x*85.0,z*85.0)-.5)*.004
                .000019 * sin((x+warp)*520.0+z*135.0) +
                    .000006 * fractal(x*500.0,z*500.0)
            }
            else -> .000018 * fractal(x*560.0,z*560.0)
        }
    }

    fun heightAt(id: String, x: Double, z: Double): Double {
        require(isSupported(id)) { "Unsupported solid-surface body: $id" }
        val warpX = fractal(x*.25+17.3,z*.25-8.1)*.72
        val warpZ = fractal(x*.31-6.2,z*.31+19.4)*.72
        val xx=x+warpX
        val zz=z+warpZ
        val broad = .11 * fractal(xx*.26,zz*.26) +
                    .043 * fractal(xx*.88,zz*.88)
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
            "europa" -> .62*broad +
                // Domain-warped cross-cutting kilometer-scale ice ridges.
                // Synthetic and deliberately not a claimed Galileo DEM.
                .035 * exp(-sin(xx*2.22+zz*.49).let { it*it } / .055) +
                .018 * exp(-sin(zz*2.85-xx*.82).let { it*it } / .13) -
                .019 * exp(-sin(xx*1.26+zz*.32).let { it*it } / .037) +
                .025 * fractal(xx*3.1, zz*3.1) +
                crater(x,z,2.4,-3.6,.65,.026)
            else -> .45*broad +
                // Titan's wind-aligned organic dunes, tens of metres
                // tall; their placement is an illustrative approximation.
                .016 * sin(xx*2.9+zz*5.9+.8*fractal(xx*.7,zz*.7)) +
                // Secondary wind-aligned dune sets resolve at the NASA-map
                // raster scale (~510m samples), unlike sub-grid pinstripes.
                // 1.5-3km spacing; height in the tens of metres on Titan.
                .008 * sin(xx*7.8+zz*2.7+.42*fractal(xx*1.5,zz*1.5)) +
                .006 * sin(xx*5.9+zz*11.7+.8*fractal(xx*1.4,zz*1.4)) +
                .016 * fractal(xx*.42,zz*.42)
        }
    }

    // Immutable per-world coarse raster (~100 KiB each). Previously a
    // collision/LOD sample recomputed four expensive FBM geologic heights
    // EVERY call; midfield reanchoring could stall the entire GL thread.
    // Geometry, collision and normals now share one cached Float raster.
    private val coarse = java.util.concurrent.ConcurrentHashMap<String, FloatArray>()
    private fun samples(id: String): FloatArray {
        require(isSupported(id))
        return coarse.computeIfAbsent(id) {
            val heights = FloatArray(SIDE*SIDE)
            val half=(SIDE-1)*.5
            for (z in 0 until SIDE) for (x in 0 until SIDE) {
                heights[z*SIDE+x] = heightAt(
                    id,(x-half)*STEP.toDouble(),(z-half)*STEP.toDouble()
                ).toFloat()
            }
            heights
        }
    }

    fun renderedHeightAt(id: String, x: Double, z: Double): Double {
        val heights=samples(id)
        val gx=x/STEP.toDouble()+(SIDE-1)*.5
        val gz=z/STEP.toDouble()+(SIDE-1)*.5
        if (!gx.isFinite() || !gz.isFinite() || gx<0 || gz<0 ||
            gx>SIDE-1 || gz>SIDE-1) return heightAt(id,x,z)
        val ix=kotlin.math.floor(gx).toInt().coerceIn(0,SIDE-2)
        val iz=kotlin.math.floor(gz).toInt().coerceIn(0,SIDE-2)
        val fx=(gx-ix).coerceIn(0.0,1.0)
        val fz=(gz-iz).coerceIn(0.0,1.0)
        val h00=heights[iz*SIDE+ix].toDouble()
        val h10=heights[iz*SIDE+ix+1].toDouble()
        val h01=heights[(iz+1)*SIDE+ix].toDouble()
        val h11=heights[(iz+1)*SIDE+ix+1].toDouble()
        return if(fx+fz<=1.0) h00*(1-fx-fz)+h10*fx+h01*fz
          else h11*(fx+fz-1.0)+h10*(1-fz)+h01*(1-fx)
    }

    fun build(id: String): MoonSurfaceMeshData {
        require(isSupported(id))
        val heights = samples(id)
        val vertices = FloatArray(SIDE*SIDE*9)
        val indices = ShortArray((SIDE-1)*(SIDE-1)*6)
        val half = (SIDE-1)/2f
        var p=0
        for (zi in 0 until SIDE) for(xi in 0 until SIDE) {
            val x=(xi-half)*STEP; val z=(zi-half)*STEP
            val h=heights[zi*SIDE+xi]
            // The GPU actually renders coarse sampled triangles, not the
            // analytic FBM between them. Match normals to the real mesh so
            // the 21m/510m LOD handoff has coherent brightness and facets.
            val x0=(xi-1).coerceAtLeast(0); val x1=(xi+1).coerceAtMost(SIDE-1)
            val z0=(zi-1).coerceAtLeast(0); val z1=(zi+1).coerceAtMost(SIDE-1)
            val slopeX=(heights[zi*SIDE+x1]-heights[zi*SIDE+x0])/
                ((x1-x0)*STEP)
            val slopeZ=(heights[z1*SIDE+xi]-heights[z0*SIDE+xi])/
                ((z1-z0)*STEP)
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
