#!/usr/bin/env python3
"""Apply API 26 biometric compatibility to the verified Later source tree."""
from pathlib import Path

root = Path(__file__).resolve().parents[2]
main = root / "app/src/main/java/com/night/later/MainActivity.kt"
text = main.read_text()

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
    raise SystemExit("could not find top-level declaration after authenticateUser")
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
