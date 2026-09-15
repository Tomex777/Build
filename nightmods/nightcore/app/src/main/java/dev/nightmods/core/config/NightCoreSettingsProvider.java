package dev.nightmods.core.config;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

/** Narrow IPC bridge between Night Mods / scoped target apps and Night Core settings. */
public final class NightCoreSettingsProvider extends ContentProvider {
    public static final String AUTHORITY = "dev.nightmods.core.settings";
    public static final String METHOD_GET_BUBBLE_STYLE = "get_bubble_style";
    public static final String METHOD_SET_BUBBLE_STYLE = "set_bubble_style";

    private static final String NIGHT_MODS_PACKAGE = "org.lsposed.manager";
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private static final String INSTAGRAM_PACKAGE = "com.instagram.android";

    @Override public boolean onCreate() { return true; }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        var context = getContext();
        if (context == null) throw new IllegalStateException("Night Core unavailable");

        if (METHOD_GET_BUBBLE_STYLE.equals(method)) {
            enforceReadCaller();
            var prefs = NightCorePreferences.open(context);
            Bundle result = new Bundle();
            result.putBoolean(BubbleStyleConfig.KEY_ENABLED, prefs.getBoolean(BubbleStyleConfig.KEY_ENABLED, true));
            result.putBoolean(BubbleStyleConfig.KEY_WHATSAPP, prefs.getBoolean(BubbleStyleConfig.KEY_WHATSAPP, true));
            result.putBoolean(BubbleStyleConfig.KEY_INSTAGRAM, prefs.getBoolean(BubbleStyleConfig.KEY_INSTAGRAM, true));
            result.putInt(BubbleStyleConfig.KEY_RADIUS, prefs.getInt(BubbleStyleConfig.KEY_RADIUS, 20));
            result.putInt(BubbleStyleConfig.KEY_SPACING, prefs.getInt(BubbleStyleConfig.KEY_SPACING, 6));
            return result;
        }

        if (METHOD_SET_BUBBLE_STYLE.equals(method)) {
            enforceWriteCaller();
            if (extras == null) throw new IllegalArgumentException("Missing settings bundle");
            var prefs = NightCorePreferences.open(context);
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

    private void enforceReadCaller() {
        if (isOwnUid()) return;
        if (callerHasPackage(NIGHT_MODS_PACKAGE)
                || callerHasPackage(WHATSAPP_PACKAGE)
                || callerHasPackage(INSTAGRAM_PACKAGE)) return;
        throw new SecurityException("Caller is not allowed to read Night Core settings");
    }

    private void enforceWriteCaller() {
        if (isOwnUid() || callerHasPackage(NIGHT_MODS_PACKAGE)) return;
        throw new SecurityException("Caller is not allowed to write Night Core settings");
    }

    private boolean isOwnUid() {
        return Binder.getCallingUid() == android.os.Process.myUid();
    }

    private boolean callerHasPackage(String expectedPackage) {
        var context = getContext();
        if (context == null) return false;
        PackageManager pm = context.getPackageManager();
        String[] packages = pm.getPackagesForUid(Binder.getCallingUid());
        if (packages == null) return false;
        for (String packageName : packages) {
            if (expectedPackage.equals(packageName)) return true;
        }
        return false;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        throw new UnsupportedOperationException();
    }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
}
