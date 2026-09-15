# Night Core

Night Core is the first-party Xposed module behind Night Mods.

## Why one module can target multiple apps

The module entry point routes the loaded package to a per-app adapter. The user-facing feature is `Bubble Styler`; WhatsApp and Instagram are just two different implementations underneath it.

```
Bubble Styler
  -> WhatsAppAdapter
  -> InstagramAdapter
```

This prevents the UI/product from becoming WhatsApp-specific.

## Current proof-of-life behavior

When the module is enabled and scoped to WhatsApp or Instagram, the entry layer waits for the target application's `Application.attach(Context)` lifecycle point, reads the current Bubble Styler settings, then hands the immutable config to the matching adapter. The adapters still only write a NightCore proof-of-life line to the LSPosed log; no guessed bubble hook is installed yet.

That is intentional. To implement real bubble styling safely, inspect the exact WhatsApp and Instagram versions installed on the test phone, locate stable view/resource/method hook points, then implement each adapter separately.

## Settings ownership and Android 16 transport

Night Mods is the normal configuration surface. Night Core owns the private `night_core` SharedPreferences file. Neither Night Mods nor a hooked target process reads that file directly across UIDs.

Night Core exposes a deliberately narrow exported provider:

- Night Mods (`org.lsposed.manager`) and Night Core itself may read settings.
- Scoped target packages (`com.whatsapp` and `com.instagram.android`) may read settings.
- Only Night Mods and Night Core may write settings.
- Hook-side reads happen after the target has a real Android `Context` and use `ContentResolver.call(...)` over provider IPC.
- If settings IPC is unavailable, Night Core fails closed and does not attach the feature with guessed/default state.

This avoids depending on cross-UID reads of `/data/data/dev.nightmods.core/shared_prefs`, and it avoids LSPosed's relocated legacy `xposedsharedprefs` path. The standalone Night Core activity remains only as a diagnostic fallback.

`target-probe` is a CI-only Android app using the `com.whatsapp` application ID. It has no package-visibility exception or privileged access; Android 16 smoke tests use it to prove that a separate target-like UID can read the exact settings written through Night Mods. It is not a Night Mods product artifact.

## Why legacy Xposed entry is used in this first build

LSPosed ET explicitly preserves legacy module compatibility, while its vendored modern API is API 100. The legacy entry keeps this proof-of-life project self-contained and buildable without bundling the framework's API AAR. The adapter boundary is API-agnostic, so the entry layer can be migrated to libxposed after the first rooted-device test without redesigning the product.
