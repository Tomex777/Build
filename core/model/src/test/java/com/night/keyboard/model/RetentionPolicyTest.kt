package com.night.keyboard.model

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RetentionPolicyTest {
    @Test fun twoHoursIsExactlyTwoHours() {
        val now = 1_000_000L
        assertEquals(now + 7_200_000L, RetentionPolicy.expiryFor(RetentionPreset.TWO_HOURS, now))
    }
    @Test fun pinnedStyleNeverRetentionHasNoExpiry() { assertNull(RetentionPolicy.expiryFor(RetentionPreset.NEVER, 1_000L)) }
    @Test fun endOfDayEndsAtLocalMidnightBoundary() {
        val zone = ZoneId.of("Africa/Lagos")
        val now = ZonedDateTime.of(2026, 9, 15, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        val expiry = RetentionPolicy.expiryFor(RetentionPreset.END_OF_DAY, now, zone)!!
        val expected = ZonedDateTime.of(2026, 9, 15, 23, 59, 59, 999_000_000, zone).toInstant().toEpochMilli()
        assertEquals(expected, expiry)
    }
}
