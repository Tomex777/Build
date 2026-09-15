package dev.nightmods.core.config;

import android.content.Context;
import android.content.SharedPreferences;

/** Single preference entry point for Night Core's own process. */
@SuppressWarnings("deprecation")
public final class NightCorePreferences {
    private NightCorePreferences() {}

    public static SharedPreferences open(Context context) {
        try {
            return context.getSharedPreferences(BubbleStyleConfig.PREFS, Context.MODE_WORLD_READABLE);
        } catch (SecurityException ignored) {
            return context.getSharedPreferences(BubbleStyleConfig.PREFS, Context.MODE_PRIVATE);
        }
    }
}
