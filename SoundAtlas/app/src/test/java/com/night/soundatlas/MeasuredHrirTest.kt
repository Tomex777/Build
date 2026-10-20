package com.night.soundatlas

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class MeasuredHrirTest {
    private fun fixture(positions:Int):ByteArray {
        val output=ByteArrayOutputStream()
        output.write((if(positions==7)"EHR2" else "EHR1").toByteArray())
        for(direction in 0 until positions)for(ear in 0..1)for(tap in 0 until 128) {
            val sample=if(tap==0) direction*300+ear*100 else 0
            output.write(sample and 0xff)
            output.write((sample ushr 8) and 0xff)
        }
        return output.toByteArray()
    }
    @Test fun interpolatesThirtyAndSixtyDegreeMeasurements() {
        val bank=MeasuredHrir.read(ByteArrayInputStream(fixture(7)))
        val l=FloatArray(128);val r=FloatArray(128)
        bank.kernels((PI/4).toFloat(),l,r)
        assertEquals(450f/32768f,l[0],.00001f)
        assertEquals(550f/32768f,r[0],.00001f)
        assertEquals(0f,l[1],.00001f)
    }
    @Test fun leftSourcesSwapEarsAndBackDirectionsAreBounded() {
        val bank=MeasuredHrir.read(ByteArrayInputStream(fixture(7)))
        val l=FloatArray(128);val r=FloatArray(128)
        bank.kernels((-PI/4).toFloat(),l,r)
        assertEquals(550f/32768f,l[0],.00001f)
        assertEquals(450f/32768f,r[0],.00001f)
        bank.kernels(PI.toFloat(),l,r)
        assertTrue(l[0].isFinite())
        assertTrue(r[0].isFinite())
    }
    @Test fun supportsEchoSpaceLegacyThreePointAssets() {
        val bank=MeasuredHrir.read(ByteArrayInputStream(fixture(3)))
        val l=FloatArray(128);val r=FloatArray(128)
        bank.kernels((PI/2).toFloat(),l,r)
        assertEquals(300f/32768f,l[0],.00001f)
    }
    @Test fun damagedOrUnknownAssetsFailClosed() {
        val damaged=fixture(7).copyOf(20)
        try {MeasuredHrir.read(ByteArrayInputStream(damaged));fail("should reject broken HRIR")}
        catch(_:IllegalArgumentException) {}
        try {MeasuredHrir.read(ByteArrayInputStream("fake".toByteArray()))
            fail("should reject unknown bank")}
        catch(_:IllegalArgumentException) {}
    }
}
