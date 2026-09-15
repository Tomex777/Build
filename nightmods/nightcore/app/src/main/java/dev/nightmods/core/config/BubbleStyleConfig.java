package dev.nightmods.core.config;

import de.robv.android.xposed.XSharedPreferences;

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

    public static BubbleStyleConfig loadForHook() {
        XSharedPreferences prefs = new XSharedPreferences("dev.nightmods.core", PREFS);
        prefs.reload();
        return new BubbleStyleConfig(
                prefs.getBoolean(KEY_ENABLED, true),
                prefs.getBoolean(KEY_WHATSAPP, true),
                prefs.getBoolean(KEY_INSTAGRAM, true),
                prefs.getInt(KEY_RADIUS, 20),
                prefs.getInt(KEY_SPACING, 6)
        );
    }
}
