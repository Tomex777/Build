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

When the module is enabled and scoped to WhatsApp or Instagram, the appropriate adapter loads and writes a NightCore line to the LSPosed module log. No guessed bubble hook is installed yet.

That is intentional. To implement real bubble styling safely, supply/inspect the exact WhatsApp and Instagram versions installed on the test phone, locate stable view/resource/method hook points, then implement each adapter separately.

## Settings ownership

Night Mods is the normal configuration surface. Night Core exposes a deliberately narrow provider so Night Mods can edit the same `night_core` preferences consumed by `XSharedPreferences`. The standalone Night Core activity remains only as a diagnostic fallback.

## Why legacy Xposed entry is used in this first build

LSPosed ET explicitly preserves legacy module compatibility, while its vendored modern API is API 100. The legacy entry keeps this proof-of-life project self-contained and buildable without bundling the framework's API AAR. The adapter boundary is API-agnostic, so the entry layer can be migrated to libxposed after the first device test without redesigning the product.
