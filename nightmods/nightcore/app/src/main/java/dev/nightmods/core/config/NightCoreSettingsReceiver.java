package dev.nightmods.core.config;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;

/**
 * Read-only target-process settings endpoint.
 *
 * Target apps send an explicit ordered broadcast with sender identity sharing enabled. Android
 * supplies the actual sender package/uid; Night Core never trusts caller-provided package extras.
 */
public final class NightCoreSettingsReceiver extends BroadcastReceiver {
    public static final String ACTION_GET_BUBBLE_STYLE =
            "dev.nightmods.core.action.GET_BUBBLE_STYLE";
    public static final String EXTRA_PROTOCOL_VERSION = "night_core_protocol_version";
    public static final int PROTOCOL_VERSION = 1;

    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private static final String INSTAGRAM_PACKAGE = "com.instagram.android";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_GET_BUBBLE_STYLE.equals(intent.getAction())) {
            reject("unsupported_action");
            return;
        }

        String senderPackage = getSentFromPackage();
        int senderUid = getSentFromUid();
        if (senderUid == Process.INVALID_UID || !isAllowedTarget(senderPackage)) {
            reject("unauthorized_sender");
            return;
        }

        var prefs = NightCorePreferences.open(context);
        Bundle result = new Bundle();
        result.putInt(EXTRA_PROTOCOL_VERSION, PROTOCOL_VERSION);
        result.putBoolean(BubbleStyleConfig.KEY_ENABLED,
                prefs.getBoolean(BubbleStyleConfig.KEY_ENABLED, true));
        result.putBoolean(BubbleStyleConfig.KEY_WHATSAPP,
                prefs.getBoolean(BubbleStyleConfig.KEY_WHATSAPP, true));
        result.putBoolean(BubbleStyleConfig.KEY_INSTAGRAM,
                prefs.getBoolean(BubbleStyleConfig.KEY_INSTAGRAM, true));
        result.putInt(BubbleStyleConfig.KEY_RADIUS,
                prefs.getInt(BubbleStyleConfig.KEY_RADIUS, 20));
        result.putInt(BubbleStyleConfig.KEY_SPACING,
                prefs.getInt(BubbleStyleConfig.KEY_SPACING, 6));

        setResultExtras(result);
        setResultCode(Activity.RESULT_OK);
    }

    private static boolean isAllowedTarget(String packageName) {
        return WHATSAPP_PACKAGE.equals(packageName) || INSTAGRAM_PACKAGE.equals(packageName);
    }

    private void reject(String reason) {
        setResultData(reason);
        setResultCode(Activity.RESULT_CANCELED);
    }
}
