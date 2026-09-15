package dev.nightmods.core.config;

import android.content.SharedPreferences;
import android.os.Bundle;

/** Immutable Bubble Styler settings shared by Night Mods and target adapters. */
public final class BubbleStyleConfig {
    public static final String PREFS = "night_core";
    public static final String KEY_ENABLED = "bubble_enabled";
    public static final String KEY_WHATSAPP = "target_whatsapp";
    public static final String KEY_INSTAGRAM = "target_instagram";
    public static final String KEY_RADIUS = "bubble_radius";
    public static final String KEY_SPACING = "bubble_spacing";

    public final boolean enabled;
    public final boolean whatsapp;
    public final boolean instagram;
    public final int radius;
    public final int spacing;

    public BubbleStyleConfig(boolean enabled, boolean whatsapp, boolean instagram, int radius, int spacing) {
        this.enabled = enabled;
        this.whatsapp = whatsapp;
        this.instagram = instagram;
        this.radius = clamp(radius, 0, 48);
        this.spacing = clamp(spacing, 0, 24);
    }

    public static BubbleStyleConfig defaults() {
        return new BubbleStyleConfig(true, true, true, 20, 6);
    }

    public static BubbleStyleConfig fromBundle(Bundle bundle) {
        if (bundle == null) return defaults();
        return new BubbleStyleConfig(
                bundle.getBoolean(KEY_ENABLED, true),
                bundle.getBoolean(KEY_WHATSAPP, true),
                bundle.getBoolean(KEY_INSTAGRAM, true),
                bundle.getInt(KEY_RADIUS, 20),
                bundle.getInt(KEY_SPACING, 6)
        );
    }

    public static BubbleStyleConfig fromPreferences(SharedPreferences prefs) {
        if (prefs == null) return defaults();
        return new BubbleStyleConfig(
                prefs.getBoolean(KEY_ENABLED, true),
                prefs.getBoolean(KEY_WHATSAPP, true),
                prefs.getBoolean(KEY_INSTAGRAM, true),
                prefs.getInt(KEY_RADIUS, 20),
                prefs.getInt(KEY_SPACING, 6)
        );
    }

    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putBoolean(KEY_ENABLED, enabled);
        bundle.putBoolean(KEY_WHATSAPP, whatsapp);
        bundle.putBoolean(KEY_INSTAGRAM, instagram);
        bundle.putInt(KEY_RADIUS, radius);
        bundle.putInt(KEY_SPACING, spacing);
        return bundle;
    }

    public boolean writeTo(SharedPreferences prefs) {
        if (prefs == null) return false;
        return prefs.edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putBoolean(KEY_WHATSAPP, whatsapp)
                .putBoolean(KEY_INSTAGRAM, instagram)
                .putInt(KEY_RADIUS, radius)
                .putInt(KEY_SPACING, spacing)
                .commit();
    }

    public static boolean hasValues(SharedPreferences prefs) {
        return prefs != null && (
                prefs.contains(KEY_ENABLED)
                        || prefs.contains(KEY_WHATSAPP)
                        || prefs.contains(KEY_INSTAGRAM)
                        || prefs.contains(KEY_RADIUS)
                        || prefs.contains(KEY_SPACING));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
