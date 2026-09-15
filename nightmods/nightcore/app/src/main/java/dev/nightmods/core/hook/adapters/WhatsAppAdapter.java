package dev.nightmods.core.hook.adapters;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import dev.nightmods.core.config.BubbleStyleConfig;

public final class WhatsAppAdapter implements TargetAdapter {
    @Override public String packageName() { return "com.whatsapp"; }
    @Override public String displayName() { return "WhatsApp"; }
    @Override public boolean enabled(BubbleStyleConfig config) { return config.whatsapp; }

    @Override
    public void attach(XC_LoadPackage.LoadPackageParam loadPackage, BubbleStyleConfig config) {
        // Deliberately no guessed/obfuscated hook names here.
        XposedBridge.log("NightCore: WhatsApp adapter attached; bubble hook awaiting version analysis"
                + " radius=" + config.radius + " spacing=" + config.spacing);
    }
}
