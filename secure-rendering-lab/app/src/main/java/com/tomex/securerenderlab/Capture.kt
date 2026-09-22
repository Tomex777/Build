package com.tomex.securerenderlab

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

abstract class BaseCaptureActivity : Activity() {

    private val captureRequestCode = 9001
    protected lateinit var capturePreview: ImageView
    protected lateinit var captureStatus: TextView

    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val error = intent?.getStringExtra(CaptureService.EXTRA_ERROR)
            if (error != null) {
                captureStatus.text = "Capture failed: " + error
                onObserverCaptureFinished(null, error)
                return
            }

            val path = intent?.getStringExtra(CaptureService.EXTRA_PATH)
            if (path.isNullOrBlank()) {
                val message = "Capture finished, but no image path was returned."
                captureStatus.text = message
                onObserverCaptureFinished(null, message)
                return
            }

            val bitmap = BitmapFactory.decodeFile(path)
            if (bitmap == null) {
                val message = "Capture file could not be decoded: " + path
                captureStatus.text = message
                onObserverCaptureFinished(path, message)
                return
            }

            capturePreview.setImageBitmap(bitmap)
            captureStatus.text =
                "Captured by MediaProjection · " + bitmap.width + "×" + bitmap.height +
                    "\nSaved in this app's cache for inspection."
            onObserverCaptureFinished(path, null)
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(CaptureService.ACTION_CAPTURE_READY)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(captureReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(captureReceiver, filter)
        }
    }

    override fun onStop() {
        runCatching { unregisterReceiver(captureReceiver) }
        super.onStop()
    }

    protected fun capturePanel(): LinearLayout =
        card().apply {
            addView(heading("Capture observer", 18f))
            addView(
                bodyText(
                    "Tap capture, grant Android's screen-capture prompt, and this app will request exactly one ordinary MediaProjection frame. Secure content should be missing from that captured frame."
                )
            )
            addView(
                labButton("Capture one frame") {
                    requestOneFrame()
                }
            )
            captureStatus = statusText("No capture yet.")
            addView(captureStatus)
            capturePreview = previewImage()
            addView(
                capturePreview,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

    protected fun requestOneFrame() {
        val manager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        captureStatus.text = "Waiting for Android's capture permission…"
        @Suppress("DEPRECATION")
        startActivityForResult(manager.createScreenCaptureIntent(), captureRequestCode)
    }

    protected open fun onObserverCaptureFinished(path: String?, error: String?) = Unit

    @Deprecated("Deprecated in Android framework but retained here to support API 26 without an AndroidX activity dependency.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != captureRequestCode) return

        if (resultCode != RESULT_OK || data == null) {
            captureStatus.text = "Capture permission was cancelled."
            return
        }

        captureStatus.text = "Capturing one frame…"
        val serviceIntent = Intent(this, CaptureService::class.java)
            .putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
            .putExtra(CaptureService.EXTRA_RESULT_DATA, data)

        startForegroundService(serviceIntent)
    }
}

class CaptureService : Service() {

    companion object {
        const val ACTION_CAPTURE_READY =
            "com.tomex.securerenderlab.CAPTURE_READY"
        const val EXTRA_PATH = "capture_path"
        const val EXTRA_ERROR = "capture_error"
        const val EXTRA_RESULT_CODE = "capture_result_code"
        const val EXTRA_RESULT_DATA = "capture_result_data"

        private const val CHANNEL_ID = "capture_observer"
        private const val NOTIFICATION_ID = 41
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val finished = AtomicBoolean(false)

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Capture observer",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()

        val resultCode =
            intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                ?: Activity.RESULT_CANCELED

        @Suppress("DEPRECATION")
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

        if (resultCode != Activity.RESULT_OK || resultData == null) {
            finishWithError("Missing or invalid MediaProjection consent result.")
            return START_NOT_STICKY
        }

        try {
            val manager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, resultData)
            if (projection == null) {
                finishWithError("Android did not create a MediaProjection session.")
                return START_NOT_STICKY
            }
            startOneFrameCapture()
        } catch (t: Throwable) {
            finishWithError(t.javaClass.simpleName + ": " + (t.message ?: "unknown error"))
        }

        return START_NOT_STICKY
    }

    private fun startInForeground() {
        val notification =
            if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("Secure Rendering Lab")
                    .setContentText("Capturing one observer frame")
                    .setSmallIcon(android.R.drawable.ic_menu_camera)
                    .setOngoing(true)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
                    .setContentTitle("Secure Rendering Lab")
                    .setContentText("Capturing one observer frame")
                    .setSmallIcon(android.R.drawable.ic_menu_camera)
                    .setOngoing(true)
                    .build()
            }

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startOneFrameCapture() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val density = metrics.densityDpi

        val reader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            3
        )
        imageReader = reader

        var frameCount = 0
        reader.setOnImageAvailableListener({ source ->
            val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
            frameCount += 1

            if (frameCount < 2) {
                image.close()
                return@setOnImageAvailableListener
            }

            if (!finished.compareAndSet(false, true)) {
                image.close()
                return@setOnImageAvailableListener
            }

            try {
                val file = writeImage(image)
                image.close()
                sendResult(file.absolutePath, null)
                cleanup(stopProjection = true)
            } catch (t: Throwable) {
                image.close()
                sendResult(
                    null,
                    t.javaClass.simpleName + ": " + (t.message ?: "could not save frame")
                )
                cleanup(stopProjection = true)
            }
        }, mainHandler)

        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                if (finished.compareAndSet(false, true)) {
                    sendResult(null, "MediaProjection stopped before a frame was captured.")
                    cleanup(stopProjection = false)
                }
            }
        }

        projection?.registerCallback(callback, mainHandler)

        virtualDisplay = projection?.createVirtualDisplay(
            "SecureRenderingLabObserver",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            mainHandler
        )

        if (virtualDisplay == null && finished.compareAndSet(false, true)) {
            sendResult(null, "Could not create the observer virtual display.")
            cleanup(stopProjection = true)
        }
    }

    private fun writeImage(image: Image): File {
        val plane = image.planes.first()
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val width = image.width
        val height = image.height

        val paddedWidth =
            (rowStride / pixelStride).coerceAtLeast(width)

        val padded = Bitmap.createBitmap(
            paddedWidth,
            height,
            Bitmap.Config.ARGB_8888
        )
        padded.copyPixelsFromBuffer(plane.buffer)

        val cropped =
            if (paddedWidth == width) padded
            else Bitmap.createBitmap(padded, 0, 0, width, height)

        val file = File(cacheDir, "observer-" + System.currentTimeMillis() + ".png")
        FileOutputStream(file).use { output ->
            cropped.compress(Bitmap.CompressFormat.PNG, 100, output)
        }

        if (cropped !== padded) padded.recycle()
        cropped.recycle()
        return file
    }

    private fun finishWithError(message: String) {
        if (!finished.compareAndSet(false, true)) return
        sendResult(null, message)
        cleanup(stopProjection = true)
    }

    private fun sendResult(path: String?, error: String?) {
        val result = Intent(ACTION_CAPTURE_READY)
            .setPackage(packageName)

        if (path != null) result.putExtra(EXTRA_PATH, path)
        if (error != null) result.putExtra(EXTRA_ERROR, error)
        sendBroadcast(result)
    }

    private fun cleanup(stopProjection: Boolean) {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null

        runCatching { imageReader?.close() }
        imageReader = null

        if (stopProjection) {
            runCatching { projection?.stop() }
        }
        projection = null

        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }
}
