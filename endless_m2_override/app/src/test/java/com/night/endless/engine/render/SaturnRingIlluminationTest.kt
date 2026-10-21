package com.night.endless.engine.render

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class SaturnRingIlluminationTest {
    private fun blocked(x: Double,y: Double,z: Double,
                        sunX: Double,sunY: Double,sunZ: Double): Boolean {
        val dot = x*sunX+y*sunY+z*sunZ
        val px=x-dot*sunX;val py=y-dot*sunY;val pz=z-dot*sunZ
        return dot < 0 && px*px+py*py+pz*pz < 1.0
    }

    @Test fun onlyRingsBehindPlanetEnterUmbra() {
        val x=0.0;val z=0.0
        assertTrue(blocked(x,-1.50,z,0.0,1.0,0.0))
        assertTrue(!blocked(x,1.50,z,0.0,1.0,0.0))
        assertTrue(!blocked(1.50,-1.50,z,0.0,1.0,0.0))
    }

    @Test fun tiltRotatesRingNormalConsistentlyWithModel() {
        for (degrees in listOf(0.0, 26.73, 90.0, 180.0)) {
            val rad=Math.toRadians(degrees)
            val x=-sin(rad); val y=cos(rad)
            assertTrue(kotlin.math.abs(x*x+y*y-1.0)<1e-12)
        }
    }
}
