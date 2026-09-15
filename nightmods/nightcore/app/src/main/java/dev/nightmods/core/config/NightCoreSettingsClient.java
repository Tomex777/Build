package dev.nightmods.core.config;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

/** Target-process reader for Night Core settings. Uses exported provider IPC, never cross-UID file access. */
public final class NightCoreSettingsClient {
    private static final Uri SETTINGS_URI = Uri.parse("content://" + NightCoreSettingsProvider.AUTHORITY);

    private NightCoreSettingsClient() {}

    public static BubbleStyleConfig readBubbleStyle(Context context) {
        if (context == null) throw new IllegalArgumentException("Target context is required");
        Bundle result = context.getContentResolver().call(
                SETTINGS_URI,
                NightCoreSettingsProvider.METHOD_GET_BUBBLE_STYLE,
                null,
                null
        );
        if (result == null) throw new IllegalStateException("Night Core settings provider returned no data");
        return BubbleStyleConfig.fromBundle(result);
    }
}
