# Fast Cuttlefish privileged install

This path avoids a full AOSP build.

Use an official AOSP **userdebug** Cuttlefish image, then install the system-lab APK into `system_ext/priv-app` after signing it with AOSP's public platform development key.

The GitHub Actions artifact `secure-rendering-system-lab-fast-cuttlefish` contains:

- `SecureRenderingSystemLab-platform.apk`
- `privapp-permissions-com.tomex.securerenderlab.system.xml`
- `install.sh`
- `verify.sh`

## Why this works

AOSP development builds use test signing keys from `build/target/product/security`. The fast-path APK is signed with the Android 16 AOSP `platform` test certificate so that it can match the platform signature on an AOSP development image.

This is for disposable AOSP/Cuttlefish development images only. It is not intended for production phones.

## Cuttlefish image

The official Cuttlefish documentation points to the `aosp_cf_x86_64_phone` userdebug build and a matching `cvd-host_package.tar.gz`.

After launching Cuttlefish and confirming `adb devices` sees it:

```bash
unzip secure-rendering-system-lab-fast-cuttlefish.zip
cd secure-rendering-system-lab-fast-cuttlefish
./install.sh
```

The installer uses the normal userdebug remount flow and pushes the APK/allowlist under `/system_ext`.

Then run:

```bash
./verify.sh
```

Inside the app, tap **Run privileged secure-display self-test**.

Expected progression:

```text
ordinary sideload:
  platform signature = no
  CAPTURE_SECURE_VIDEO_OUTPUT = DENIED

Cuttlefish fast path:
  platform signature = yes
  system/privileged = yes
  CAPTURE_SECURE_VIDEO_OUTPUT = GRANTED
  secure OWN_CONTENT_ONLY virtual display = SUCCESS
```

This still does not capture another app or mirror the physical display. The self-test renders only synthetic content owned by Secure Rendering System Lab.
