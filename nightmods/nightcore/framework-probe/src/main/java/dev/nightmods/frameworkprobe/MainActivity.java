package dev.nightmods.frameworkprobe;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.libxposed.service.IXposedScopeCallback;
import io.github.libxposed.service.IXposedService;

/**
 * CI-only stand-in for the LSPosed API-100 service. It intentionally implements the exact
 * service-100 AIDL so Night Core's real libxposed client writes into a process/store that is
 * outside dev.nightmods.core. This lets CI prove remote settings survive Night Core death.
 */
public final class MainActivity extends Activity {
    private static final Uri NIGHT_CORE_XPOSED_SERVICE =
            Uri.parse("content://dev.nightmods.core.XposedService");
    private static volatile Context appContext;

    private static final IXposedService.Stub FRAMEWORK = new IXposedService.Stub() {
        @Override public int getAPIVersion() { return 100; }
        @Override public String getFrameworkName() { return "Night Mods CI Framework Probe"; }
        @Override public String getFrameworkVersion() { return "API-100"; }
        @Override public long getFrameworkVersionCode() { return 100L; }
        @Override public int getFrameworkPrivilege() { return 0; }

        @Override
        public List<String> getScope() {
            return List.of("com.whatsapp", "com.instagram.android");
        }

        @Override
        public void requestScope(String packageName, IXposedScopeCallback callback) {
            if (callback == null) return;
            try {
                callback.onScopeRequestApproved(packageName);
            } catch (Throwable ignored) {
            }
        }

        @Override public String removeScope(String packageName) { return null; }

        @Override
        public Bundle requestRemotePreferences(String group) {
            Bundle result = new Bundle();
            result.putSerializable("map", new HashMap<>(remote(group).getAll()));
            return result;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void updateRemotePreferences(String group, Bundle diff) {
            if (diff == null) return;
            SharedPreferences.Editor editor = remote(group).edit();
            Object deleteValue = diff.getSerializable("delete");
            if (deleteValue instanceof Set<?>) {
                for (Object key : (Set<?>) deleteValue) {
                    if (key instanceof String) editor.remove((String) key);
                }
            }

            Object putValue = diff.getSerializable("put");
            if (putValue instanceof Map<?, ?>) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) putValue).entrySet()) {
                    if (!(entry.getKey() instanceof String key)) continue;
                    putValue(editor, key, entry.getValue());
                }
            }
            if (!editor.commit()) {
                throw new IllegalStateException("Framework probe could not persist remote preferences");
            }
        }

        @Override
        public void deleteRemotePreferences(String group) {
            if (!remote(group).edit().clear().commit()) {
                throw new IllegalStateException("Framework probe could not delete remote preferences");
            }
        }

        @Override public String[] listRemoteFiles() { return new String[0]; }
        @Override public ParcelFileDescriptor openRemoteFile(String name) { return null; }
        @Override public boolean deleteRemoteFile(String name) { return false; }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context application = getApplicationContext();
        appContext = application == null ? this : application;

        var result = getSharedPreferences("framework_probe", MODE_PRIVATE);
        try {
            Bundle extras = new Bundle();
            extras.putBinder("binder", FRAMEWORK);
            Bundle response = getContentResolver().call(
                    NIGHT_CORE_XPOSED_SERVICE, "SendBinder", null, extras);
            result.edit()
                    .clear()
                    .putBoolean("bound", response != null)
                    .commit();
        } catch (Throwable error) {
            result.edit()
                    .clear()
                    .putBoolean("bound", false)
                    .putString("error", error.getClass().getName() + ": " + error.getMessage())
                    .commit();
        } finally {
            finish();
        }
    }

    private static SharedPreferences remote(String group) {
        Context context = appContext;
        if (context == null) throw new IllegalStateException("Framework probe context unavailable");
        String safeGroup = group == null ? "default" : group.replaceAll("[^A-Za-z0-9_.-]", "_");
        return context.getSharedPreferences("framework_remote_" + safeGroup, Context.MODE_PRIVATE);
    }

    @SuppressWarnings("unchecked")
    private static void putValue(SharedPreferences.Editor editor, String key, Object value) {
        if (value == null) {
            editor.remove(key);
        } else if (value instanceof String) {
            editor.putString(key, (String) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Set<?>) {
            Set<String> strings = new HashSet<>();
            for (Object item : (Set<?>) value) {
                if (item instanceof String) strings.add((String) item);
            }
            editor.putStringSet(key, strings);
        } else {
            throw new IllegalArgumentException("Unsupported remote preference type for " + key);
        }
    }
}
