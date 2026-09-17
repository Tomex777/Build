package dev.nightmods.core.config;

import android.content.SharedPreferences;
import android.os.Bundle;

/** Immutable System UI settings shared by Night Mods and Night Core's SystemUI adapter. */
public final class SystemUiConfig {
    public static final String KEY_ENABLED = "systemui_enabled";
    public static final String KEY_STATUSBAR_PADDING_ENABLED = "statusbar_padding_enabled";
    public static final String KEY_STATUSBAR_PADDING_DP = "statusbar_padding_dp";

    public final boolean enabled;
    public final boolean statusBarPaddingEnabled;
    public final int statusBarPaddingDp;

    public SystemUiConfig(boolean enabled, boolean statusBarPaddingEnabled, int statusBarPaddingDp) {
        this.enabled = enabled;
        this.statusBarPaddingEnabled = statusBarPaddingEnabled;
        this.statusBarPaddingDp = clamp(statusBarPaddingDp, 0, 32);
    }

    public static SystemUiConfig defaults() {
        // Phone-level hooks are opt-in. Installing Night Core alone must not alter SystemUI.
        return new SystemUiConfig(false, false, 8);
    }

    public static SystemUiConfig fromBundle(Bundle bundle) {
        if (bundle == null) return defaults();
        return new SystemUiConfig(
                bundle.getBoolean(KEY_ENABLED, false),
                bundle.getBoolean(KEY_STATUSBAR_PADDING_ENABLED, false),
                bundle.getInt(KEY_STATUSBAR_PADDING_DP, 8));
    }

    public static SystemUiConfig fromPreferences(SharedPreferences prefs) {
        if (prefs == null) return defaults();
        return new SystemUiConfig(
                prefs.getBoolean(KEY_ENABLED, false),
                prefs.getBoolean(KEY_STATUSBAR_PADDING_ENABLED, false),
                prefs.getInt(KEY_STATUSBAR_PADDING_DP, 8));
    }

    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putBoolean(KEY_ENABLED, enabled);
        bundle.putBoolean(KEY_STATUSBAR_PADDING_ENABLED, statusBarPaddingEnabled);
        bundle.putInt(KEY_STATUSBAR_PADDING_DP, statusBarPaddingDp);
        return bundle;
    }

    public boolean writeTo(SharedPreferences prefs) {
        if (prefs == null) return false;
        return prefs.edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putBoolean(KEY_STATUSBAR_PADDING_ENABLED, statusBarPaddingEnabled)
                .putInt(KEY_STATUSBAR_PADDING_DP, statusBarPaddingDp)
                .commit();
    }

    public static boolean hasValues(SharedPreferences prefs) {
        return prefs != null && (
                prefs.contains(KEY_ENABLED)
                        || prefs.contains(KEY_STATUSBAR_PADDING_ENABLED)
                        || prefs.contains(KEY_STATUSBAR_PADDING_DP));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
