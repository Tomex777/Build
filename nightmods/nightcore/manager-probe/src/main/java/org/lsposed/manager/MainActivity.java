package org.lsposed.manager;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;

/** CI-only probe that reproduces Night Mods bootstrapping Night Core through its provider. */
public final class MainActivity extends Activity {
    private static final Uri SETTINGS_URI = Uri.parse("content://dev.nightmods.core.settings");
    private static final String METHOD_GET = "get_bubble_style";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        var prefs = getSharedPreferences("night_core_manager_probe", MODE_PRIVATE);
        try {
            Bundle result = getContentResolver().call(SETTINGS_URI, METHOD_GET, null, null);
            if (result == null) throw new IllegalStateException("Night Core returned no settings");
            prefs.edit()
                    .clear()
                    .putBoolean("success", true)
                    .putBoolean("bubble_enabled", result.getBoolean("bubble_enabled", true))
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
