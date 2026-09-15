package dev.nightmods.core.config;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/**
 * Keeps Night Core's editable app-side mirror synchronized with LSPosed's framework-owned
 * remote preferences. Hooked apps read the framework store directly and never need to wake
 * the Night Core Android process.
 */
public final class NightCoreServiceStore {
    private static final String STATE_PREFS = "night_core_service_state";
    private static final String KEY_PENDING_REMOTE_SYNC = "pending_remote_sync";

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final AtomicReference<XposedService> SERVICE = new AtomicReference<>();
    private static volatile Context appContext;

    private NightCoreServiceStore() {}

    public static void initialize(Context context) {
        if (context == null) return;
        Context application = context.getApplicationContext();
        appContext = application == null ? context : application;
        if (!INITIALIZED.compareAndSet(false, true)) return;

        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(XposedService service) {
                SERVICE.set(service);
                Context current = appContext;
                if (current != null) syncOnBind(current, service);
            }

            @Override
            public void onServiceDied(XposedService service) {
                SERVICE.compareAndSet(service, null);
            }
        });
    }

    public static BubbleStyleConfig read(Context context) {
        if (context == null) throw new IllegalArgumentException("Night Core context is required");
        initialize(context);
        SharedPreferences local = NightCorePreferences.open(context);
        XposedService service = SERVICE.get();
        if (service != null) {
            try {
                SharedPreferences remote = service.getRemotePreferences(BubbleStyleConfig.PREFS);
                if (BubbleStyleConfig.hasValues(remote)) {
                    BubbleStyleConfig config = BubbleStyleConfig.fromPreferences(remote);
                    config.writeTo(local);
                    setPending(context, false);
                    return config;
                }
            } catch (RuntimeException ignored) {
                // Framework binder may have died between lookup and read. Local mirror remains valid.
            }
        }
        return BubbleStyleConfig.fromPreferences(local);
    }

    public static boolean write(Context context, BubbleStyleConfig config) {
        if (context == null) throw new IllegalArgumentException("Night Core context is required");
        if (config == null) throw new IllegalArgumentException("Night Core settings are required");
        initialize(context);

        boolean localCommitted = config.writeTo(NightCorePreferences.open(context));
        boolean remoteCommitted = writeRemote(SERVICE.get(), config);
        setPending(context, !remoteCommitted);
        return localCommitted;
    }

    private static boolean writeRemote(XposedService service, BubbleStyleConfig config) {
        if (service == null) return false;
        try {
            SharedPreferences remote = service.getRemotePreferences(BubbleStyleConfig.PREFS);
            return remote != null && config.writeTo(remote);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void syncOnBind(Context context, XposedService service) {
        try {
            SharedPreferences remote = service.getRemotePreferences(BubbleStyleConfig.PREFS);
            if (remote == null) return;

            SharedPreferences local = NightCorePreferences.open(context);
            boolean pending = state(context).getBoolean(KEY_PENDING_REMOTE_SYNC, false);
            if (pending) {
                if (BubbleStyleConfig.fromPreferences(local).writeTo(remote)) {
                    setPending(context, false);
                }
                return;
            }

            if (BubbleStyleConfig.hasValues(remote)) {
                BubbleStyleConfig.fromPreferences(remote).writeTo(local);
            } else if (BubbleStyleConfig.fromPreferences(local).writeTo(remote)) {
                setPending(context, false);
            }
        } catch (RuntimeException ignored) {
            setPending(context, true);
        }
    }

    private static SharedPreferences state(Context context) {
        return context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE);
    }

    private static void setPending(Context context, boolean pending) {
        state(context).edit().putBoolean(KEY_PENDING_REMOTE_SYNC, pending).apply();
    }
}
