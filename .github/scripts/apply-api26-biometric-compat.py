#!/usr/bin/env python3
"""Apply API 26 biometric compatibility to the verified Later source tree."""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply-api26-biometric-compat.py SOURCE_ROOT")
root = Path(sys.argv[1])
main = root / "app/src/main/java/com/night/later/MainActivity.kt"
text = main.read_text()

recents_before = """            LaunchedEffect(
                settings.hideInRecents
            ) {
                setRecentsScreenshotEnabled(
                    !settings.hideInRecents
                )
            }
"""
recents_after = """            LaunchedEffect(settings.hideInRecents) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    setRecentsScreenshotEnabled(!settings.hideInRecents)
                } else if (settings.hideInRecents) {
                    window.addFlags(
                        android.view.WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(
                        android.view.WindowManager.LayoutParams.FLAG_SECURE
                    )
                }
            }
"""
if text.count(recents_before) != 1:
    raise SystemExit("expected exactly one unguarded recents-screenshot call")
text = text.replace(recents_before, recents_after, 1)

for obsolete in (
    "import androidx.biometric.BiometricPrompt\n",
    "import androidx.core.content.ContextCompat\n",
    "import androidx.fragment.app.FragmentActivity\n",
):
    text = text.replace(obsolete, "")

anchor = "import androidx.activity.ComponentActivity\n"
if text.count(anchor) != 1:
    raise SystemExit("expected exactly one ComponentActivity import")
imports = """import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.AuthenticationRequest
import androidx.biometric.AuthenticationResult
import androidx.biometric.AuthenticationResultCallback
import androidx.biometric.AuthenticationRequest.Companion.biometricRequest
import androidx.biometric.AuthenticationRequest.Companion.credentialRequest
import androidx.biometric.registerForAuthenticationResult
"""
text = text.replace(anchor, anchor + imports, 1)

old_class = "class MainActivity :\n    FragmentActivity() {"
if text.count(old_class) != 1:
    raise SystemExit("expected FragmentActivity MainActivity declaration")
fields = """class MainActivity :
    ComponentActivity() {

    private var pendingBiometricCompletion: ((Boolean) -> Unit)? = null
    private var pendingCredentialCompletion: ((Boolean) -> Unit)? = null

    private val biometricAuthenticationLauncher =
        registerForAuthenticationResult(
            object : AuthenticationResultCallback {
                override fun onAuthResult(result: AuthenticationResult) {
                    val callback = pendingBiometricCompletion
                    pendingBiometricCompletion = null
                    callback?.invoke(result is AuthenticationResult.Success)
                }

                override fun onAuthAttemptFailed() = Unit
            }
        )

    private val deviceCredentialLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val callback = pendingCredentialCompletion
            pendingCredentialCompletion = null
            callback?.invoke(result.resultCode == RESULT_OK)
        }

    internal fun launchBiometricAuthentication(
        request: AuthenticationRequest,
        completion: (Boolean) -> Unit
    ) {
        pendingBiometricCompletion = completion
        biometricAuthenticationLauncher.launch(request)
    }

    internal fun launchDeviceCredentialAuthentication(
        intent: android.content.Intent,
        completion: (Boolean) -> Unit
    ) {
        pendingCredentialCompletion = completion
        deviceCredentialLauncher.launch(intent)
    }

    internal fun clearAuthenticationCompletion(completion: (Boolean) -> Unit) {
        if (pendingBiometricCompletion === completion) {
            pendingBiometricCompletion = null
        }
        if (pendingCredentialCompletion === completion) {
            pendingCredentialCompletion = null
        }
    }
"""
text = text.replace(old_class, fields, 1)

start_marker = "private suspend fun authenticateUser("
if text.count(start_marker) != 1:
    raise SystemExit("expected exactly one authenticateUser helper")
start = text.index(start_marker)
end = text.find("\nprivate ", start + len(start_marker))
if end < 0:
    end = len(text)
helper = """private suspend fun authenticateUser(
    activity: MainActivity,
    method: UnlockMethod,
    title: String,
    subtitle: String
) =
    suspendCancellableCoroutine { continuation ->
        val completion: (Boolean) -> Unit = { authenticated ->
            if (continuation.isActive) {
                continuation.resume(authenticated)
            }
        }
        continuation.invokeOnCancellation {
            activity.clearAuthenticationCompletion(completion)
        }

        try {
            if (
                method == UnlockMethod.DEVICE_CREDENTIAL &&
                Build.VERSION.SDK_INT < Build.VERSION_CODES.R
            ) {
                val keyguard =
                    activity.getSystemService(KeyguardManager::class.java)
                val intent =
                    keyguard?.createConfirmDeviceCredentialIntent(title, subtitle)
                if (intent == null) {
                    completion(false)
                } else {
                    activity.launchDeviceCredentialAuthentication(intent, completion)
                }
            } else {
                val request =
                    if (method == UnlockMethod.DEVICE_CREDENTIAL) {
                        credentialRequest(title) {
                            setSubtitle(subtitle)
                        }
                    } else {
                        biometricRequest(
                            title,
                            AuthenticationRequest.Biometric.Fallback.DeviceCredential
                        ) {
                            setSubtitle(subtitle)
                            setMinStrength(
                                AuthenticationRequest.Biometric.Strength.Class3()
                            )
                        }
                    }
                activity.launchBiometricAuthentication(request, completion)
            }
        } catch (_: Exception) {
            activity.clearAuthenticationCompletion(completion)
            completion(false)
        }
    }
"""
text = text[:start] + helper + text[end:]
if "FragmentActivity" in text or "BiometricPrompt" in text or "ContextCompat" in text:
    raise SystemExit("obsolete FragmentActivity biometric implementation remains")
if text.count("registerForAuthenticationResult(") != 1:
    raise SystemExit("expected ComponentActivity biometric launcher")

main.write_text(text)

versions = root / "gradle/libs.versions.toml"
v = versions.read_text()
if v.count('biometric = "1.1.0"') != 1:
    raise SystemExit("expected AndroidX Biometric 1.1.0 pin")
versions.write_text(v.replace('biometric = "1.1.0"', 'biometric = "1.4.0-alpha07"', 1))


# ImageDecoder was introduced in API 28. Keep it for current Android while
# decoding downsampled images with BitmapFactory on the API 26-27 baseline.
media_path = root / "app/src/main/java/com/night/later/data/media/MediaDraftPreparer.kt"
media = media_path.read_text()
for old, new in (
    ("import android.graphics.Bitmap\n", "import android.graphics.Bitmap\nimport android.graphics.BitmapFactory\n"),
    ("import android.graphics.ImageDecoder\n", "import android.graphics.ImageDecoder\nimport android.os.Build\n"),
):
    if media.count(old) != 1:
        raise SystemExit(f"expected exactly one import anchor {old.strip()!r}")
    media = media.replace(old, new, 1)

decode_start = "        val source =\n            ImageDecoder"
decode_end = "        val outputFile =\n            File("
if media.count(decode_start) != 1 or media.count(decode_end) != 1:
    raise SystemExit("expected exactly one ImageDecoder block in MediaDraftPreparer")
start = media.index(decode_start)
end = media.index(decode_end, start)
media = (
    media[:start]
    + "        val bitmap = decodeScaledBitmap(context, sourceUri, maxSide)\n\n"
    + media[end:]
)

helper_anchor = "    private fun originalImageExtension(\n"
if media.count(helper_anchor) != 1:
    raise SystemExit("expected exactly one originalImageExtension helper")
compat_helper = """    private fun decodeScaledBitmap(
        context: Context,
        sourceUri: Uri,
        maxSide: Int
    ): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source =
                ImageDecoder.createSource(
                    context.contentResolver,
                    sourceUri
                )
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
                val width = info.size.width
                val height = info.size.height
                val longest = maxOf(width, height)
                if (longest > maxSide) {
                    val scale = maxSide.toFloat() / longest.toFloat()
                    decoder.setTargetSize(
                        (width * scale).toInt().coerceAtLeast(1),
                        (height * scale).toInt().coerceAtLeast(1)
                    )
                }
            }
        }

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        openReadableMediaStream(context, sourceUri).use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            error("Unable to decode selected image dimensions.")
        }

        var sampleSize = 1
        while (
            maxOf(bounds.outWidth / (sampleSize * 2), bounds.outHeight / (sampleSize * 2)) >= maxSide
        ) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded =
            openReadableMediaStream(context, sourceUri).use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: error("Unable to decode selected image.")

        val longest = maxOf(decoded.width, decoded.height)
        if (longest <= maxSide) return decoded

        val scale = maxSide.toFloat() / longest.toFloat()
        val resized = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true
        )
        if (resized !== decoded) decoded.recycle()
        return resized
    }

    private fun openReadableMediaStream(
        context: Context,
        sourceUri: Uri
    ): java.io.InputStream {
        context.contentResolver.openFileDescriptor(sourceUri, "r")?.let { descriptor ->
            return android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
        }
        return context.contentResolver.openInputStream(sourceUri)
            ?: error("Unable to open selected media.")
    }

"""
media = media.replace(helper_anchor, compat_helper + helper_anchor, 1)
media_path.write_text(media)


# Keep media-import failures diagnosable on pre-28 release devices. The
# UI intentionally stays generic, but QA can distinguish decoder/provider
# failures from picker harness problems using the captured stack trace.
editor = root / "app/src/main/java/com/night/later/ui/editor/CapsuleEditorScreen.kt"
editor_text = editor.read_text()

# AndroidX Photo Picker falls back to a temporary URI grant before API 33.
# Use ACTION_OPEN_DOCUMENT on API 26-32 so the selected content remains readable
# while Later copies it into its own media draft cache.
build_import = "import android.os.SystemClock\n"
if editor_text.count(build_import) != 1:
    raise SystemExit("expected exactly one SystemClock import in CapsuleEditorScreen")
editor_text = editor_text.replace(
    build_import,
    "import android.os.Build\n" + build_import,
    1
)
picker_before = """        runCatching {
            mediaLauncher.launch(
                PickVisualMediaRequest(
                    ActivityResultContracts
                        .PickVisualMedia
                        .ImageAndVideo
                )
            )
        }.onFailure {
"""
picker_after = """        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mediaLauncher.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts
                            .PickVisualMedia
                            .ImageAndVideo
                    )
                )
            } else {
                documentLauncher.launch(
                    arrayOf("image/*", "video/*")
                )
            }
        }.onFailure {
"""
if editor_text.count(picker_before) != 1:
    raise SystemExit("expected exactly one legacy media picker launch")
editor_text = editor_text.replace(picker_before, picker_after, 1)

log_import_anchor = "import android.os.SystemClock\n"
if editor_text.count(log_import_anchor) != 1:
    raise SystemExit("expected exactly one SystemClock import after media picker patch")
editor_text = editor_text.replace(
    log_import_anchor,
    log_import_anchor + "import android.util.Log\n",
    1
)

for function_name, message in (
    ("launchMediaPicker", "Could not launch the media picker"),
    ("launchDocumentPicker", "Could not launch the file picker"),
):
    function_start = editor_text.index(f"    fun {function_name}() {{")
    function_end = editor_text.find("\n    fun ", function_start + 1)
    if function_end < 0:
        function_end = len(editor_text)
    function_text = editor_text[function_start:function_end]
    failure_anchor = "        }.onFailure {\n"
    if function_text.count(failure_anchor) != 1:
        raise SystemExit(f"expected one failure handler in {function_name}")
    logged_failure = (
        "        }.onFailure { error ->\n"
        + f'            Log.e("LaterMediaPicker", "{message}", error)\n'
    )
    function_text = function_text.replace(failure_anchor, logged_failure, 1)
    editor_text = editor_text[:function_start] + function_text + editor_text[function_end:]
failure_anchor = "                        }.getOrNull()\n"
if editor_text.count(failure_anchor) != 2:
    raise SystemExit("expected exactly two media preparation result handlers")
failure_log = """                        }.onFailure { error ->
                            Log.e(
                                "LaterMediaImport",
                                "Failed to prepare selected media (scheme=${uri.scheme}, authority=${uri.authority})",
                                error
                            )
                        }.getOrNull()
"""
editor_text = editor_text.replace(failure_anchor, failure_log, 2)
# Android 9 and earlier require READ_EXTERNAL_STORAGE for shared media files.
manifest = root / "app/src/main/AndroidManifest.xml"
manifest_text = manifest.read_text()
permission_anchor = '    <uses-permission android:name="android.permission.RECORD_AUDIO" />\n'
permission = '    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="28" />\n'
if manifest_text.count(permission_anchor) != 1:
    raise SystemExit("expected one RECORD_AUDIO permission anchor")
if permission not in manifest_text:
    manifest_text = manifest_text.replace(permission_anchor, permission_anchor + permission, 1)
manifest.write_text(manifest_text)

media_launch_anchor = "    val microphonePermissionLauncher =\n"
media_support = """    fun launchMediaPickerNow() {
        onExternalActivityStateChanged(true)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mediaLauncher.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts
                            .PickVisualMedia
                            .ImageAndVideo
                    )
                )
            } else {
                documentLauncher.launch(
                    arrayOf("image/*", "video/*")
                )
            }
        }.onFailure { error ->
            Log.e("LaterMediaPicker", "Could not launch the media picker", error)
            onExternalActivityStateChanged(false)
            recorderMessage = "Couldn't open the media picker."
        }
    }

    val mediaStoragePermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                launchMediaPickerNow()
            } else {
                recorderMessage =
                    "Allow storage access to import media on this Android version."
            }
        }

"""
if editor_text.count(media_launch_anchor) != 1:
    raise SystemExit("expected exactly one microphone permission launcher")
editor_text = editor_text.replace(media_launch_anchor, media_support + media_launch_anchor, 1)

picker_function_start = editor_text.index("    fun launchMediaPicker() {")
picker_function_end = editor_text.index("\n    fun launchDocumentPicker()", picker_function_start)
permission_wrapper = """    fun launchMediaPicker() {
        val needsLegacyStoragePermission =
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED

        if (needsLegacyStoragePermission) {
            mediaStoragePermissionLauncher.launch(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        } else {
            launchMediaPickerNow()
        }
    }
"""
editor_text = (
    editor_text[:picker_function_start]
    + permission_wrapper
    + editor_text[picker_function_end:]
)
editor.write_text(editor_text)

# The cached image viewer/editor must also support API 26 and API 27.
# Android ImageDecoder is API 28+, and a missing OnHeaderDecodedListener
# prevented cached thumbnails from ever leaving their loading spinner.
viewer_path = root / "app/src/main/java/com/night/later/ui/media/LaterMediaViewer.kt"
viewer_text = viewer_path.read_text()
# Existing image/editor source already imports BitmapFactory elsewhere.
# Do not insert a duplicate import: Kotlin considers that ambiguous.
if viewer_text.count("import android.graphics.BitmapFactory\n") != 1:
    raise SystemExit("expected one BitmapFactory import in LaterMediaViewer")
if viewer_text.count("import android.graphics.ImageDecoder\n") != 1:
    raise SystemExit("expected one ImageDecoder import in LaterMediaViewer")
old_decode = """    return runCatching {
        ImageDecoder.decodeBitmap(
            ImageDecoder.createSource(file)
        ) { decoder, info, _ ->
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
            val width = info.size.width
            val height = info.size.height
            val longest = maxOf(width, height)
            if (longest > maxDimension && maxDimension > 0) {
                val scale = maxDimension.toFloat() / longest.toFloat()
                decoder.setTargetSize(
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1)
                )
            }
        }
    }.getOrNull()
"""
new_decode = """    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(file)
            ) { decoder, info, _ ->
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
                val width = info.size.width
                val height = info.size.height
                val longest = maxOf(width, height)
                if (longest > maxDimension && maxDimension > 0) {
                    val scale = maxDimension.toFloat() / longest.toFloat()
                    decoder.setTargetSize(
                        (width * scale).toInt().coerceAtLeast(1),
                        (height * scale).toInt().coerceAtLeast(1)
                    )
                }
            }
        } else {
            // Pre-API 28: bounds-first decode to avoid large bitmap allocations.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@runCatching null
            }
            var sampling = 1
            if (maxDimension > 0) {
                while (maxOf(
                    bounds.outWidth / (sampling * 2),
                    bounds.outHeight / (sampling * 2)
                ) >= maxDimension) {
                    sampling *= 2
                }
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampling
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
                ?: return@runCatching null
            val longest = maxOf(decoded.width, decoded.height)
            if (maxDimension <= 0 || longest <= maxDimension) {
                decoded
            } else {
                val scale = maxDimension.toFloat() / longest.toFloat()
                val resized = Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt().coerceAtLeast(1),
                    (decoded.height * scale).toInt().coerceAtLeast(1),
                    true
                )
                if (resized !== decoded) decoded.recycle()
                resized
            }
        }
    }.getOrNull()
"""
if viewer_text.count(old_decode) != 1:
    raise SystemExit("expected exactly one cached image decoder")
viewer_path.write_text(viewer_text.replace(old_decode, new_decode, 1))

# The full-screen image viewer/editor also decodes cached image files. Its
# original decodeMediaBitmap() used ImageDecoder unconditionally, trapping
# API 26-27 in an endless spinner (ImageDecoder exists only on API 28+).
# Decode real files through BitmapFactory with two-pass sampling on old Android.
viewer_path = root / "app/src/main/java/com/night/later/ui/media/LaterMediaViewer.kt"
viewer_text = viewer_path.read_text()
image_import = "import android.graphics.ImageDecoder\n"
if viewer_text.count(image_import) != 1:
    raise SystemExit("expected one ImageDecoder import in LaterMediaViewer")
viewer_text = viewer_text.replace(
    image_import,
    "import android.graphics.BitmapFactory\n" + image_import,
    1
)
bitmap_start = "fun decodeMediaBitmap(\n"
bitmap_end = "\nprivate fun formatMediaDuration("
if viewer_text.count(bitmap_start) != 1 or viewer_text.count(bitmap_end) != 1:
    raise SystemExit("expected exactly one bitmap decoder and next duration helper")
start = viewer_text.index(bitmap_start)
end = viewer_text.index(bitmap_end, start)
old_decoder = viewer_text[start:end]
if "ImageDecoder.createSource(file)" not in old_decoder:
    raise SystemExit("Later cached image decoder no longer matches expected source")
compatible_decoder = """fun decodeMediaBitmap(
    path: String,
    maxDimension: Int = 1800
): Bitmap? {
    val file = File(path)
    if (!file.isFile) return null

    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(file)
            ) { decoder, info, _ ->
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
                val width = info.size.width
                val height = info.size.height
                val longest = maxOf(width, height)
                if (longest > maxDimension && maxDimension > 0) {
                    val scale = maxDimension.toFloat() / longest.toFloat()
                    decoder.setTargetSize(
                        (width * scale).toInt().coerceAtLeast(1),
                        (height * scale).toInt().coerceAtLeast(1)
                    )
                }
            }
        } else {
            // API 26-27: read bounds without allocating full-resolution pixels.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@runCatching null
            }

            var sampleSize = 1
            if (maxDimension > 0) {
                while (
                    maxOf(
                        bounds.outWidth / (sampleSize * 2),
                        bounds.outHeight / (sampleSize * 2)
                    ) >= maxDimension
                ) {
                    sampleSize *= 2
                }
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded =
                BitmapFactory.decodeFile(file.absolutePath, options)
                    ?: return@runCatching null
            val largest = maxOf(decoded.width, decoded.height)
            if (maxDimension <= 0 || largest <= maxDimension) {
                decoded
            } else {
                val scale = maxDimension.toFloat() / largest.toFloat()
                val resized = Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt().coerceAtLeast(1),
                    (decoded.height * scale).toInt().coerceAtLeast(1),
                    true
                )
                if (resized !== decoded) decoded.recycle()
                resized
            }
        }
    }.getOrNull()
}
"""
viewer_text = viewer_text[:start] + compatible_decoder + viewer_text[end:]
viewer_path.write_text(viewer_text)
