package com.whatsapp;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;

/** CI-only probe proving target/foreign UIDs cannot read any Night Core manager settings. */
public final class MainActivity extends Activity {
    private static final Uri SETTINGS_URI = Uri.parse("content://dev.nightmods.core.settings");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        var resultPrefs = getSharedPreferences("night_core_probe", MODE_PRIVATE);

        boolean bubbleDenied = false;
        boolean systemUiDenied = false;
        boolean bubbleDisclosed = false;
        boolean systemUiDisclosed = false;
        String bubbleError = "";
        String systemUiError = "";

        try {
            Bundle result = getContentResolver().call(SETTINGS_URI, "get_bubble_style", null, null);
            bubbleDisclosed = result != null;
        } catch (Throwable error) {
            bubbleDenied = true;
            bubbleError = error.getClass().getName() + ": " + error.getMessage();
        }

        try {
            Bundle result = getContentResolver().call(SETTINGS_URI, "get_system_ui", null, null);
            systemUiDisclosed = result != null;
        } catch (Throwable error) {
            systemUiDenied = true;
            systemUiError = error.getClass().getName() + ": " + error.getMessage();
        }

        resultPrefs.edit()
                .clear()
                .putBoolean("access_denied", bubbleDenied && systemUiDenied)
                .putBoolean("settings_disclosed", bubbleDisclosed || systemUiDisclosed)
                .putBoolean("bubble_access_denied", bubbleDenied)
                .putBoolean("systemui_access_denied", systemUiDenied)
                .putBoolean("bubble_settings_disclosed", bubbleDisclosed)
                .putBoolean("systemui_settings_disclosed", systemUiDisclosed)
                .putString("bubble_error", bubbleError)
                .putString("systemui_error", systemUiError)
                .commit();
        finish();
    }
}
