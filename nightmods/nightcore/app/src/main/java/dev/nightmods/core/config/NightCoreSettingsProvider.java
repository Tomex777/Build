package dev.nightmods.core.config;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Narrow IPC bridge used by Night Mods to edit Night Core hook-readable preferences. */
public final class NightCoreSettingsProvider extends ContentProvider {
    public static final String AUTHORITY = "dev.nightmods.core.settings";
    public static final String METHOD_GET_BUBBLE_STYLE = "get_bubble_style";
    public static final String METHOD_SET_BUBBLE_STYLE = "set_bubble_style";

    private static final String NIGHT_MODS_PACKAGE = "org.lsposed.manager";

    @Override public boolean onCreate() { return true; }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        enforceNightModsCaller();
        var context = getContext();
        if (context == null) return Bundle.EMPTY;
        var prefs = NightCorePreferences.open(context);

        if (METHOD_GET_BUBBLE_STYLE.equals(method)) {
            Bundle result = new Bundle();
            result.putBoolean(BubbleStyleConfig.KEY_ENABLED, prefs.getBoolean(BubbleStyleConfig.KEY_ENABLED, true));
            result.putBoolean(BubbleStyleConfig.KEY_WHATSAPP, prefs.getBoolean(BubbleStyleConfig.KEY_WHATSAPP, true));
            result.putBoolean(BubbleStyleConfig.KEY_INSTAGRAM, prefs.getBoolean(BubbleStyleConfig.KEY_INSTAGRAM, true));
            result.putInt(BubbleStyleConfig.KEY_RADIUS, prefs.getInt(BubbleStyleConfig.KEY_RADIUS, 20));
            result.putInt(BubbleStyleConfig.KEY_SPACING, prefs.getInt(BubbleStyleConfig.KEY_SPACING, 6));
            return result;
        }

        if (METHOD_SET_BUBBLE_STYLE.equals(method)) {
            if (extras == null) throw new IllegalArgumentException("Missing settings bundle");
            prefs.edit()
                    .putBoolean(BubbleStyleConfig.KEY_ENABLED, extras.getBoolean(BubbleStyleConfig.KEY_ENABLED, true))
                    .putBoolean(BubbleStyleConfig.KEY_WHATSAPP, extras.getBoolean(BubbleStyleConfig.KEY_WHATSAPP, true))
                    .putBoolean(BubbleStyleConfig.KEY_INSTAGRAM, extras.getBoolean(BubbleStyleConfig.KEY_INSTAGRAM, true))
                    .putInt(BubbleStyleConfig.KEY_RADIUS, clamp(extras.getInt(BubbleStyleConfig.KEY_RADIUS, 20), 0, 48))
                    .putInt(BubbleStyleConfig.KEY_SPACING, clamp(extras.getInt(BubbleStyleConfig.KEY_SPACING, 6), 0, 24))
                    .apply();
            return Bundle.EMPTY;
        }

        throw new IllegalArgumentException("Unknown Night Core settings method: " + method);
    }

    private void enforceNightModsCaller() {
        var context = getContext();
        if (context == null) throw new SecurityException("Night Core unavailable");
        int uid = Binder.getCallingUid();
        if (uid == android.os.Process.myUid()) return;
        PackageManager pm = context.getPackageManager();
        String[] packages = pm.getPackagesForUid(uid);
        if (packages != null) {
            for (String packageName : packages) {
                if (NIGHT_MODS_PACKAGE.equals(packageName)) return;
            }
        }
        throw new SecurityException("Caller is not Night Mods");
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Nullable @Override public Cursor query(@NonNull Uri uri, @Nullable String[] projection,
            @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        throw new UnsupportedOperationException();
    }
    @Nullable @Override public String getType(@NonNull Uri uri) { return null; }
    @Nullable @Override public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) { throw new UnsupportedOperationException(); }
}
