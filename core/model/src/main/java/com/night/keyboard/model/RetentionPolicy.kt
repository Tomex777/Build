package com.night.keyboard.model

import java.time.Instant
import java.time.ZoneId

object RetentionPolicy {
    fun expiryFor(preset: RetentionPreset, nowMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long? = when (preset) {
        RetentionPreset.NEVER -> null
        RetentionPreset.END_OF_DAY -> Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        else -> nowMillis + requireNotNull(preset.minutes) * 60_000L
    }
}
