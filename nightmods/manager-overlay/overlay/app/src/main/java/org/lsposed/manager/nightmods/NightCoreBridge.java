package org.lsposed.manager.nightmods;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Manager-side client for Night Core's deliberately narrow settings provider. */
public final class NightCoreBridge {
    public static final String PACKAGE_NAME = "dev.nightmods.core";
    public static final String AUTHORITY = "dev.nightmods.core.settings";
    public static final Uri SETTINGS_URI = Uri.parse("content://" + AUTHORITY);
    private static final String METHOD_GET = "get_bubble_style";
    private static final String METHOD_SET = "set_bubble_style";
    public static final String KEY_ENABLED = "bubble_enabled";
    public static final String KEY_WHATSAPP = "target_whatsapp";
    public static final String KEY_INSTAGRAM = "target_instagram";
    public static final String KEY_RADIUS = "bubble_radius";
    public static final String KEY_SPACING = "bubble_spacing";

    private NightCoreBridge() {}

    public static boolean isInstalled(@NonNull Context context) {
        try {
            context.getPackageManager().getPackageInfo(PACKAGE_NAME, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    @NonNull
    public static String versionName(@NonNull Context context) {
        try {
            var info = context.getPackageManager().getPackageInfo(PACKAGE_NAME, 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            return "";
        }
    }

    @Nullable
    public static BubbleStyleState loadBubbleStyle(@NonNull Context context) {
        if (!isInstalled(context)) return null;
        try {
            Bundle data = context.getContentResolver().call(SETTINGS_URI, METHOD_GET, null, null);
            if (data == null) return null;
            return new BubbleStyleState(
                    data.getBoolean(KEY_ENABLED, true),
                    data.getBoolean(KEY_WHATSAPP, true),
                    data.getBoolean(KEY_INSTAGRAM, true),
                    clamp(data.getInt(KEY_RADIUS, 20), 0, 48),
                    clamp(data.getInt(KEY_SPACING, 6), 0, 24));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static boolean saveBubbleStyle(@NonNull Context context, @NonNull BubbleStyleState state) {
        if (!isInstalled(context)) return false;
        Bundle data = new Bundle();
        data.putBoolean(KEY_ENABLED, state.enabled);
        data.putBoolean(KEY_WHATSAPP, state.whatsapp);
        data.putBoolean(KEY_INSTAGRAM, state.instagram);
        data.putInt(KEY_RADIUS, clamp(state.radius, 0, 48));
        data.putInt(KEY_SPACING, clamp(state.spacing, 0, 24));
        try {
            context.getContentResolver().call(SETTINGS_URI, METHOD_SET, null, data);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Nullable
    public static NightModsBackend.ModuleRow frameworkModule() {
        if (!NightModsBackend.isFrameworkActive()) return null;
        for (NightModsBackend.ModuleRow row : NightModsBackend.modules()) {
            if (PACKAGE_NAME.equals(row.packageName)) return row;
        }
        return null;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class BubbleStyleState {
        public final boolean enabled;
        public final boolean whatsapp;
        public final boolean instagram;
        public final int radius;
        public final int spacing;

        public BubbleStyleState(boolean enabled, boolean whatsapp, boolean instagram, int radius, int spacing) {
            this.enabled = enabled;
            this.whatsapp = whatsapp;
            this.instagram = instagram;
            this.radius = radius;
            this.spacing = spacing;
        }
    }
}
