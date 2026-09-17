package dev.nightmods.core.config;

import android.content.SharedPreferences;

/** Hook-side aggregate of all Night Core capability settings in the framework store. */
public final class NightCoreConfig {
    public final BubbleStyleConfig bubbleStyle;
    public final SystemUiConfig systemUi;

    public NightCoreConfig(BubbleStyleConfig bubbleStyle, SystemUiConfig systemUi) {
        this.bubbleStyle = bubbleStyle == null ? BubbleStyleConfig.defaults() : bubbleStyle;
        this.systemUi = systemUi == null ? SystemUiConfig.defaults() : systemUi;
    }

    public static NightCoreConfig fromPreferences(SharedPreferences preferences) {
        return new NightCoreConfig(
                BubbleStyleConfig.fromPreferences(preferences),
                SystemUiConfig.fromPreferences(preferences));
    }
}
