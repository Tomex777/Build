package com.night.sora.ui.player;

import is.xyz.mpv.MPVLib;

/**
 * Null-safe bridge for mpv JNI getters.
 *
 * aniyomi-mpv-lib documents that these native getters can return null before
 * the property exists, even though Kotlin sees platform types that may be
 * auto-unboxed too early.
 */
public final class MpvPropertyReader {
    private MpvPropertyReader() {}

    public static boolean getBoolean(String property, boolean fallback) {
        try {
            Boolean value = MPVLib.getPropertyBoolean(property);
            return value != null ? value.booleanValue() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static double getDouble(String property, double fallback) {
        try {
            Double value = MPVLib.getPropertyDouble(property);
            return value != null ? value.doubleValue() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static String getString(String property, String fallback) {
        try {
            String value = MPVLib.getPropertyString(property);
            return value != null ? value : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
