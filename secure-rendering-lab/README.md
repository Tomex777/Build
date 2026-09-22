# Secure Rendering Lab

An installable Android lab for observing how Android separates physical display output from ordinary capture output.

## Android config

- compileSdk 36
- targetSdk 36
- minSdk 26
- JDK 17
- Android Gradle Plugin 8.13.2
- Media3 1.11.1

## Experiments

### FLAG_SECURE window

Toggles WindowManager.LayoutParams.FLAG_SECURE on the Activity and then requests one ordinary MediaProjection frame.

Expected: the physical display still shows the Activity while the capture path omits or blacks the protected window.

The 0.2 lab also has a controlled self-bypass: the Activity deliberately clears its own FLAG_SECURE immediately before one capture and restores it afterwards. This demonstrates policy removal by the protected app itself, not an OS-level bypass.

### Secure SurfaceView

Places a normal SurfaceView and a SurfaceView created with setSecure(true) in one Activity.

Expected: both are visible on the physical display; the secure surface should be omitted from an insecure capture.

The lab can additionally:
- intentionally render the same secure test secret into a normal View, demonstrating an app-level source-data leak before secure composition
- run PixelCopy against both SurfaceViews and report the platform result codes

### DRM inspector

Shows:

- Widevine support
- ClearKey support
- Widevine plugin vendor/version/description
- current Widevine session security level when a session can be opened
- secure-named AVC/HEVC decoder components exposed by MediaCodecList

The playback experiment uses the public DASH-IF/Axinom ClearKey test vector:

https://media.axprod.net/TestVectors/v7-MultiDRM-SingleKey/Manifest_1080p_ClearKey.mpd

ClearKey is intentionally used as a transparent DRM teaching sample. It demonstrates MediaDrm key negotiation and encrypted sample playback, but it is not equivalent to hardware-secure Widevine L1 protected rendering.

## Capture observer

Each experiment can request a one-frame MediaProjection capture. Android's system consent dialog is shown every time. The capture runs in a foreground service and stores a PNG only in the app cache.

## Scope

This project does not:

- request privileged CAPTURE_SECURE_VIDEO_OUTPUT access
- patch or hook SurfaceFlinger
- capture secure content from other apps
- bypass Widevine or other DRM systems

It is a play box for seeing Android's documented protection boundaries on a real device. Controlled exposure modes only reveal this app's own synthetic test content.
