package org.lsposed.manager;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;

/** CI-only probe that reproduces Night Mods bootstrapping and writing Night Core settings. */
public final class MainActivity extends Activity {
    private static final Uri SETTINGS_URI = Uri.parse("content://dev.nightmods.core.settings");
    private static final String METHOD_GET = "get_bubble_style";
    private static final String METHOD_SET = "set_bubble_style";
    private static final String METHOD_TARGET_STATUS = "get_target_status";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        var prefs = getSharedPreferences("night_core_manager_probe", MODE_PRIVATE);
        try {
            Bundle desired = new Bundle();
            desired.putBoolean("bubble_enabled", false);
            desired.putBoolean("target_whatsapp", true);
            desired.putBoolean("target_instagram", false);
            desired.putInt("bubble_radius", 31);
            desired.putInt("bubble_spacing", 9);
            getContentResolver().call(SETTINGS_URI, METHOD_SET, null, desired);

            Bundle result = getContentResolver().call(SETTINGS_URI, METHOD_GET, null, null);
            if (result == null) throw new IllegalStateException("Night Core returned no settings");

            Bundle whatsapp = getContentResolver().call(
                    SETTINGS_URI, METHOD_TARGET_STATUS, "com.whatsapp", null);
            Bundle instagram = getContentResolver().call(
                    SETTINGS_URI, METHOD_TARGET_STATUS, "com.instagram.android", null);
            if (whatsapp == null || instagram == null) {
                throw new IllegalStateException("Night Core returned no target metadata");
            }

            prefs.edit()
                    .clear()
                    .putBoolean("success", true)
                    .putBoolean("bubble_enabled", result.getBoolean("bubble_enabled", true))
                    .putBoolean("target_whatsapp", result.getBoolean("target_whatsapp", false))
                    .putBoolean("target_instagram", result.getBoolean("target_instagram", true))
                    .putInt("bubble_radius", result.getInt("bubble_radius", -1))
                    .putInt("bubble_spacing", result.getInt("bubble_spacing", -1))
                    .putBoolean("whatsapp_installed", whatsapp.getBoolean("installed", false))
                    .putString("whatsapp_compatibility", whatsapp.getString("compatibility", "UNKNOWN"))
                    .putString("whatsapp_version_name", whatsapp.getString("version_name", ""))
                    .putBoolean("instagram_installed", instagram.getBoolean("installed", false))
                    .putString("instagram_compatibility", instagram.getString("compatibility", "UNKNOWN"))
                    .commit();
        } catch (Throwable error) {
            prefs.edit()
                    .clear()
                    .putBoolean("success", false)
                    .putString("error", error.getClass().getName() + ": " + error.getMessage())
                    .commit();
        } finally {
            finish();
        }
    }
}
