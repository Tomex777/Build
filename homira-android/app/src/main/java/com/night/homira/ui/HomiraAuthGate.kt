package com.night.homira.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.night.homira.R
import com.night.homira.data.HomiraLiveRepository
import com.night.homira.data.LiveProfile
import com.night.homira.data.LiveContact
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class LiveGateState {
    Loading,
    SignedOut,
    SignedIn
}

private fun normalizePhoneE164(input: String): String? {
    val trimmed = input.trim().replace(" ", "").replace("-", "")
    if (!trimmed.startsWith("+")) return null
    val digits = trimmed.drop(1)
    if (digits.length !in 8..15 || digits.any { !it.isDigit() }) return null
    return "+$digits"
}

private fun friendlyProfileSetupError(error: Throwable): String {
    val message = error.message.orEmpty().lowercase()
    return when {
        "duplicate" in message || "unique" in message ->
            "That username or phone number is already linked to another Homira account."
        else -> "Could not finish setting up your profile. Try again."
    }
}

private fun friendlyAuthError(error: Throwable): String {
    val message = error.message.orEmpty().lowercase()

    return when {
        "email_provider_disabled" in message ||
            "unsupported email provider" in message ->
            "Email sign-in is not available right now."

        "phone_provider_disabled" in message ||
            "unsupported phone provider" in message ->
            "Phone sign-in is not available yet."

        "rate limit" in message ||
            "too many requests" in message ||
            "over_request_rate_limit" in message ->
            "Too many attempts. Wait a moment and try again."

        "invalid" in message && ("otp" in message || "token" in message) ->
            "That code is invalid or has expired."

        "expired" in message && ("otp" in message || "token" in message) ->
            "That code has expired. Request a new one."

        "timeout" in message ||
            "unable to resolve host" in message ||
            "network" in message ||
            "connect" in message ->
            "Couldn't connect. Check your internet connection and try again."

        else -> "Could not continue. Try again."
    }
}

@Composable
fun HomiraAuthGate(
    requestedCallId: String? = null,
    requestedAnswerCall: Boolean = false
) {
    val repository = remember { HomiraLiveRepository() }
    var gateState by remember { mutableStateOf(LiveGateState.Loading) }

    LaunchedEffect(Unit) {
        runCatching { repository.initialize() }
        gateState = if (repository.isSignedIn()) {
            LiveGateState.SignedIn
        } else {
            LiveGateState.SignedOut
        }
    }

    when (gateState) {
        LiveGateState.Loading -> HomiraTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(HomiraBackground),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = HomiraGreen)
            }
        }

        LiveGateState.SignedOut -> EmailOtpScreen(
            repository = repository,
            onSignedIn = { gateState = LiveGateState.SignedIn }
        )

        LiveGateState.SignedIn -> LiveProfileHost(
            repository = repository,
            requestedCallId = requestedCallId,
            requestedAnswerCall = requestedAnswerCall,
            onSignedOut = { gateState = LiveGateState.SignedOut }
        )
    }
}

@Composable
private fun EmailOtpScreen(
    repository: HomiraLiveRepository,
    onSignedIn: () -> Unit
) {
    HomiraTheme {
        val scope = rememberCoroutineScope()
        var email by remember { mutableStateOf("") }
        var otp by remember { mutableStateOf("") }
        var codeSent by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var resendCooldownSeconds by remember { mutableStateOf(0) }

        LaunchedEffect(resendCooldownSeconds) {
            if (resendCooldownSeconds > 0) {
                delay(1_000)
                resendCooldownSeconds -= 1
            }
        }

        BackHandler(enabled = codeSent && !busy) {
            codeSent = false
            otp = ""
            error = null
            resendCooldownSeconds = 0
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HomiraBackground)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.homira_call_companion),
                    contentDescription = "Homira call companion",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.58f)
                        .height(176.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Homira",
                    color = HomiraText,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (codeSent) {
                        "Enter the verification code sent to $email"
                    } else {
                        "Sign in to your call hub"
                    },
                    color = HomiraMuted,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(28.dp))

                if (!codeSent) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.trim() },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Email address") },
                        placeholder = { Text("you@example.com") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = RoundedCornerShape(18.dp)
                    )
                } else {
                    OutlinedTextField(
                        value = otp,
                        onValueChange = { otp = it.filter(Char::isDigit).take(10) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Verification code") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        shape = RoundedCornerShape(18.dp)
                    )
                }

                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = error ?: "",
                        color = HomiraDanger,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            error = null
                            runCatching {
                                if (!codeSent) {
                                    require(
                                        email.contains("@") &&
                                            email.substringAfter("@").contains(".")
                                    ) {
                                        "Enter a valid email address."
                                    }
                                    repository.sendEmailOtp(email)
                                    codeSent = true
                                    resendCooldownSeconds = 60
                                } else {
                                    require(otp.length in 6..10) { "Enter the verification code." }
                                    repository.verifyEmailOtp(email, otp)
                                    onSignedIn()
                                }
                            }.onFailure {
                                Log.e("HomiraAuth", "Email authentication failed", it)
                                error = friendlyAuthError(it)
                            }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HomiraGreen,
                        contentColor = HomiraBackground
                    )
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            color = HomiraBackground,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            if (codeSent) "Verify and continue" else "Send code",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (codeSent) {
                    Spacer(Modifier.height(10.dp))
                    TextButton(
                        onClick = {
                            scope.launch {
                                busy = true
                                error = null
                                runCatching {
                                    repository.sendEmailOtp(email)
                                    resendCooldownSeconds = 60
                                }.onFailure {
                                    Log.e("HomiraAuth", "Email OTP resend failed", it)
                                    error = friendlyAuthError(it)
                                }
                                busy = false
                            }
                        },
                        enabled = !busy && resendCooldownSeconds == 0,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (resendCooldownSeconds > 0) {
                                "Resend code in ${resendCooldownSeconds}s"
                            } else {
                                "Resend code"
                            },
                            color = if (resendCooldownSeconds > 0) {
                                HomiraMuted
                            } else {
                                HomiraGreen
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = {
                            codeSent = false
                            otp = ""
                            error = null
                            resendCooldownSeconds = 0
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HomiraSurface,
                            contentColor = HomiraText
                        )
                    ) {
                        Text("Change email")
                    }
                }

                Spacer(Modifier.height(22.dp))
                Text(
                    text = "Your call audio, video and screen share are not stored in Supabase.",
                    color = HomiraMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}


@Composable
private fun LiveProfileHost(
    repository: HomiraLiveRepository,
    requestedCallId: String?,
    requestedAnswerCall: Boolean,
    onSignedOut: () -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    var profile by remember { mutableStateOf<LiveProfile?>(null) }
    var contacts by remember { mutableStateOf<List<LiveContact>>(emptyList()) }

    LaunchedEffect(Unit) {
        profile = repository.loadMyProfile()
        contacts = runCatching { repository.loadContacts() }.getOrDefault(emptyList())
        loading = false
    }

    when {
        loading -> HomiraTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(HomiraBackground),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = HomiraGreen)
            }
        }

        profile == null ||
            profile?.displayName.isNullOrBlank() ||
            profile?.phoneE164.isNullOrBlank() -> {
            ProfileSetupScreen(
                repository = repository,
                existingProfile = profile,
                onComplete = { saved ->
                    profile = saved
                },
                onSignOut = onSignedOut
            )
        }

        else -> HomiraProductionApp(
            initialProfile = profile,
            initialContacts = contacts,
            liveMode = true,
            requestedCallId = requestedCallId,
            requestedAnswerCall = requestedAnswerCall,
            onSignedOut = onSignedOut
        )
    }
}

@Composable
private fun ProfileSetupScreen(
    repository: HomiraLiveRepository,
    existingProfile: LiveProfile?,
    onComplete: (LiveProfile) -> Unit,
    onSignOut: () -> Unit
) {
    HomiraTheme {
        val scope = rememberCoroutineScope()
        var name by remember { mutableStateOf(existingProfile?.displayName.orEmpty()) }
        var username by remember { mutableStateOf(existingProfile?.username.orEmpty()) }
        var phone by remember { mutableStateOf(existingProfile?.phoneE164.orEmpty()) }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HomiraBackground)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Set up your Homira profile",
                    color = HomiraText,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Your phone number lets other Homira users find and call you. Verification codes still go to your email.",
                    color = HomiraMuted,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Name") },
                    shape = RoundedCornerShape(18.dp)
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                            .lowercase()
                            .filter { char -> char.isLetterOrDigit() || char == '_' }
                            .take(30)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Username") },
                    placeholder = { Text("homira_user") },
                    shape = RoundedCornerShape(18.dp)
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.take(20) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Phone number") },
                    placeholder = { Text("+234…") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(18.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Use the full international format, for example +234…",
                    color = HomiraMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = error.orEmpty(),
                        color = HomiraDanger,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        scope.launch {
                            val normalizedPhone = normalizePhoneE164(phone)
                            when {
                                name.trim().length < 2 -> {
                                    error = "Enter your name."
                                }
                                username.length !in 3..30 -> {
                                    error = "Username must be 3–30 characters."
                                }
                                normalizedPhone == null -> {
                                    error = "Enter a valid phone number with country code."
                                }
                                else -> {
                                    busy = true
                                    error = null
                                    runCatching {
                                        repository.completeMyProfile(
                                            displayName = name,
                                            username = username,
                                            phoneE164 = normalizedPhone
                                        )
                                    }.onSuccess(onComplete)
                                        .onFailure {
                                            Log.e("HomiraAuth", "Profile setup failed", it)
                                            error = friendlyProfileSetupError(it)
                                        }
                                    busy = false
                                }
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HomiraGreen,
                        contentColor = HomiraBackground
                    )
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            color = HomiraBackground,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Continue", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(8.dp))
                TextButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            runCatching { repository.signOut() }
                            onSignOut()
                        }
                    }
                ) {
                    Text("Use another email", color = HomiraMuted)
                }
            }
        }
    }
}
