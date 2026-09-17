# Night Core

Night Core is the first-party Xposed capability engine behind Night Mods.

Night Mods remains the normal user-facing configuration surface. Night Core is a separate APK and should not grow a duplicate settings UI.

## Architecture

Night Core has one modern libxposed module entry and routes supported target packages to per-app adapters:

```text
Night Mods
  -> Night Core
       -> Bubble Styler
            -> WhatsAppAdapter
            -> InstagramAdapter
```

The product feature is `Bubble Styler`; WhatsApp and Instagram are separate target implementations underneath it. This keeps the UI and feature model target-neutral.

## Hook entry

Night Core uses the libxposed API-100 module entry:

```text
dev.nightmods.core.hook.NightCoreHook
```

The entry is declared through `META-INF/xposed/java_init.list`; the old `assets/xposed_init` legacy entry is no longer used.

For WhatsApp or Instagram, the module waits for `Application.attach(Context)`, reads the framework-owned remote preferences through API 100, resolves the exact target version, checks adapter compatibility, and only then allows the adapter to attach.

Settings retrieval is performed off the target app's main thread so target startup is not blocked by preference loading.

## Version gating

Unknown WhatsApp and Instagram builds are intentionally not hooked.

Current adapters return `ANALYSIS_REQUIRED` until an exact target APK/version has been inspected and explicitly added as supported. Do not add guessed or obfuscated class names from another build.

A real Bubble Styler hook is therefore still pending exact target analysis. The current adapters do not claim actual WhatsApp or Instagram bubble styling works.

## Settings ownership

The editable settings are:

- `bubble_enabled`
- `target_whatsapp`
- `target_instagram`
- `bubble_radius`
- `bubble_spacing`

Night Core owns the local app-side mirror. Night Mods writes through Night Core's manager-only provider; Night Mods does not maintain a duplicate settings database.

The provider is not a hook-side transport. Target applications and unrelated UIDs are denied manager settings access.

## libxposed API-100 framework preference transport

Night Core synchronizes the editable local mirror with the libxposed API/service-100 remote preference store.

```text
Night Mods
  -> Night Core manager provider
  -> NightCoreServiceStore
  -> libxposed service API 100 remote preferences
  -> LSPosed framework-owned store
  -> NightCoreHook.getRemotePreferences(...)
  -> WhatsApp / Instagram adapter
```

The app side uses `XposedServiceHelper` / `XposedService.getRemotePreferences(...)` to write the framework store. The hook side uses `XposedModule.getRemotePreferences(...)` and does not call back into the Night Core Android app process.

This is the process-death guarantee Night Core needs: once a setting has been committed to the framework store, killing `dev.nightmods.core` does not remove the hook-visible copy and a hooked target does not need to wake the Night Core Android process to read it.

A manager write is only reported as successful after both the local mirror and the framework remote preference commit succeed. If the framework Binder is temporarily unavailable, the local value is retained as pending but the write is not falsely reported as hook-effective. When the API-100 service Binder is delivered again, Night Core synchronizes the pending state.

## Android 16 CI coverage

The CI probes are test-only APKs and are not Night Mods product artifacts:

- `manager-probe` uses package `org.lsposed.manager` and exercises the manager provider contract.
- `target-probe` uses package `com.whatsapp` and proves a target-like UID cannot read the manager provider.
- `attacker-probe` reuses the same request code under package `dev.nightmods.attacker` and proves authorization is based on Android caller identity rather than request content.
- `framework-probe` implements the exact API-100 service AIDL and provides an external framework-owned preference store for Android 16 process-death testing.

The security workflow writes distinctive Bubble Styler values, verifies they reach the framework-owned store, kills the Night Core Android process, verifies the remote settings remain unchanged while Night Core stays dead, and then models API-100 Binder redelivery to the restarted module app process.

## Standalone launcher

The standalone Night Core activity is an engine/status page only. It shows engine version, Night Mods detection, and target adapter state.

Its **Open Night Mods** action opens the explicit `lsposed://night-core` deep link scoped to `org.lsposed.manager`, with launcher fallback only when the deep link cannot be handled.
