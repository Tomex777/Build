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
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: error("Unable to open selected image.")
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
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
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
failure_anchor = "                        }.getOrNull()\n"
if editor_text.count(failure_anchor) != 2:
    raise SystemExit("expected exactly two media preparation result handlers")
failure_log = """                        }.onFailure { error ->
                            Log.e(
                                "LaterMediaImport",
                                "Failed to prepare selected media",
                                error
                            )
                        }.getOrNull()
"""
editor_text = editor_text.replace(failure_anchor, failure_log, 2)
editor.write_text(editor_text)
