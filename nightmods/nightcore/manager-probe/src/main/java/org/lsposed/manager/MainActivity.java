package org.lsposed.manager;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;

/** CI-only probe that reproduces Night Mods bootstrapping Night Core through its provider. */
public final class MainActivity extends Activity {
    private static final Uri SETTINGS_URI = Uri.parse("content://dev.nightmods.core.settings");
    private static final String METHOD_GET = "get_bubble_style";
    private static final String METHOD_TARGET_STATUS = "get_target_status";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        var prefs = getSharedPreferences("night_core_manager_probe", MODE_PRIVATE);
        try {
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
