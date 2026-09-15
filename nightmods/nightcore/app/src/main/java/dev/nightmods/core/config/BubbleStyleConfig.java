package dev.nightmods.core.config;

import android.os.Bundle;

/** Immutable Bubble Styler settings shared by the manager bridge and target adapters. */
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
        this.radius = radius;
        this.spacing = spacing;
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
                clamp(bundle.getInt(KEY_RADIUS, 20), 0, 48),
                clamp(bundle.getInt(KEY_SPACING, 6), 0, 24)
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
