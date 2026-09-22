# Secure Rendering System Lab 0.4

This is the AOSP/userdebug edition of Secure Rendering Lab.

It is intentionally limited to lab-owned synthetic content. The system build can verify privileged secure-display capability and can expose SurfaceFlinger / DisplayManager permission decisions through diagnostic logs, but it does not include a global secure-layer screenshot implementation.

## What changes compared with the normal APK

The Soong module installs as a privileged `system_ext` app, is signed with the platform certificate, and requests:

- `android.permission.CAPTURE_SECURE_VIDEO_OUTPUT`
- `android.permission.CAPTURE_VIDEO_OUTPUT`
- `android.permission.CAPTURE_BLACKOUT_CONTENT`
- `android.permission.READ_FRAME_BUFFER`

The app reports the actual grant state at runtime.

`CAPTURE_SECURE_VIDEO_OUTPUT` is used by Android's secure virtual-display gate. `CAPTURE_BLACKOUT_CONTENT` is a separate SurfaceFlinger permission used when a capture request explicitly asks to include secure layers.

## Safe self-test

The **Run privileged secure-display self-test** button creates:

```text
VIRTUAL_DISPLAY_FLAG_SECURE
+
VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
```

and renders a Presentation containing only synthetic text created by this app. It never requests mirroring of the physical display.

The app hashes a small sample of the resulting frame so you can verify that a secure virtual display was actually created and rendered.

## AOSP integration

Copy this directory into an Android 16 AOSP checkout:

```bash
./install_into_aosp.sh /path/to/aosp
```

To also install diagnostic-only framework patches:

```bash
./install_into_aosp.sh /path/to/aosp --apply-diagnostics
```

Then include this product fragment from the userdebug product you intend to build:

```make
$(call inherit-product-if-exists, packages/apps/SecureRenderingSystemLab/product/secure_rendering_system_lab.mk)
```

After choosing your normal AOSP userdebug target:

```bash
source build/envsetup.sh
lunch <your-userdebug-target>
m SecureRenderingSystemLab
```

For a full image, build/boot your chosen product normally after adding the product fragment.

## Diagnostics

The optional patches only add logs. They do **not** change the permission decisions.

Watch them with:

```bash
adb logcat -s SurfaceFlinger DisplayManagerService
```

Look for the prefix:

```text
[SecureRenderingLab]
```

The SurfaceFlinger patch logs secure-layer capture requests and whether the caller has `CAPTURE_BLACKOUT_CONTENT`. The DisplayManagerService patch logs secure virtual-display requests and whether the projection/permission gate succeeded.

## GitHub Actions sanity APK

The repository also builds this same Java source as a normal SDK 36 APK. That APK is only a compile/runtime sanity check and **will not** gain platform privileges when sideloaded. The actual privileged behavior requires the AOSP platform-signed system build.
