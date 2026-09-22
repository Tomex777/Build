package com.tomex.securerenderlab;

import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.view.Display;

import java.io.OutputStream;
import java.lang.reflect.Method;

/**
 * Shizuku user service. On an unrooted phone started through Wireless debugging/ADB,
 * Shizuku runs this as Android's shell UID.
 *
 * This requests captureSecureLayers=true and deliberately keeps allowProtected=false.
 */
public final class ShizukuSecureCaptureService extends ISecureCaptureService.Stub {
    private volatile String lastStatus = "Service created; no capture yet.";

    public ShizukuSecureCaptureService() {
    }

    @Override
    public int getServiceUid() {
        return Process.myUid();
    }

    @Override
    public String getLastStatus() {
        return lastStatus;
    }

    @Override
    public ParcelFileDescriptor captureSecureDisplay() {
        Bitmap bitmap = captureSecureBitmap();
        if (bitmap == null) {
            return null;
        }

        try {
            final ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            final ParcelFileDescriptor writeSide = pipe[1];

            new Thread(() -> {
                try (OutputStream output =
                         new ParcelFileDescriptor.AutoCloseOutputStream(writeSide)) {
                    boolean ok = bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
                    if (!ok) {
                        lastStatus = lastStatus + " | PNG compression returned false";
                    }
                } catch (Throwable t) {
                    lastStatus = lastStatus + " | stream error: " + describe(t);
                } finally {
                    bitmap.recycle();
                }
            }, "secure-capture-png").start();

            return pipe[0];
        } catch (Throwable t) {
            bitmap.recycle();
            lastStatus = "Pipe creation failed: " + describe(t);
            return null;
        }
    }

    private Bitmap captureSecureBitmap() {
        try {
            Class<?> builderClass =
                Class.forName("android.window.ScreenCapture$CaptureArgs$Builder");
            Object builder = builderClass.getDeclaredConstructor().newInstance();

            Method setCaptureSecureLayers =
                builderClass.getMethod("setCaptureSecureLayers", boolean.class);
            setCaptureSecureLayers.invoke(builder, true);

            try {
                Method setAllowProtected =
                    builderClass.getMethod("setAllowProtected", boolean.class);
                setAllowProtected.invoke(builder, false);
            } catch (NoSuchMethodException ignored) {
            }

            Method build = builderClass.getMethod("build");
            Object captureArgs = build.invoke(builder);

            Class<?> screenCaptureClass = Class.forName("android.window.ScreenCapture");
            Method createSync =
                screenCaptureClass.getDeclaredMethod("createSyncCaptureListener");
            Object syncListener = createSync.invoke(null);

            Class<?> windowManagerGlobal =
                Class.forName("android.view.WindowManagerGlobal");
            Method getWmService =
                windowManagerGlobal.getMethod("getWindowManagerService");
            Object wmService = getWmService.invoke(null);

            Class<?> captureArgsClass =
                Class.forName("android.window.ScreenCapture$CaptureArgs");
            Class<?> listenerClass =
                Class.forName("android.window.ScreenCapture$ScreenCaptureListener");

            Method captureDisplay = wmService.getClass().getMethod(
                "captureDisplay",
                int.class,
                captureArgsClass,
                listenerClass
            );

            captureDisplay.invoke(
                wmService,
                Display.DEFAULT_DISPLAY,
                captureArgs,
                syncListener
            );

            Method getBuffer = syncListener.getClass().getMethod("getBuffer");
            Object screenshotHardwareBuffer = getBuffer.invoke(syncListener);
            if (screenshotHardwareBuffer == null) {
                lastStatus =
                    "WindowManager returned no screenshot buffer. UID=" + Process.myUid();
                return null;
            }

            boolean containsSecureLayers = false;
            try {
                Method containsSecure =
                    screenshotHardwareBuffer.getClass().getMethod("containsSecureLayers");
                containsSecureLayers =
                    (Boolean) containsSecure.invoke(screenshotHardwareBuffer);
            } catch (Throwable ignored) {
            }

            Method asBitmap =
                screenshotHardwareBuffer.getClass().getMethod("asBitmap");
            Bitmap hardwareBitmap = (Bitmap) asBitmap.invoke(screenshotHardwareBuffer);
            if (hardwareBitmap == null) {
                lastStatus =
                    "ScreenshotHardwareBuffer.asBitmap() returned null. UID=" + Process.myUid();
                return null;
            }

            Bitmap readable = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false);
            if (readable == null) {
                lastStatus =
                    "Could not copy screenshot into an ordinary bitmap. UID=" + Process.myUid();
                return null;
            }

            lastStatus =
                "SUCCESS | service UID=" + Process.myUid()
                    + " | secure layers reported=" + containsSecureLayers
                    + " | size=" + readable.getWidth() + "x" + readable.getHeight()
                    + " | protected DRM requested=false";
            return readable;
        } catch (Throwable t) {
            lastStatus =
                "Secure-layer capture failed | service UID=" + Process.myUid()
                    + " | " + describe(t);
            return null;
        }
    }

    private static String describe(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return cause.getClass().getSimpleName()
            + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
