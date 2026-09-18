package com.night.homira.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.night.homira.data.HomiraLiveRepository
import com.night.homira.data.LiveProfile
import com.night.homira.data.LiveContact
import kotlinx.coroutines.launch

private enum class LiveGateState {
    Loading,
    SignedOut,
    SignedIn
}

@Composable
fun HomiraAuthGate() {
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

        LiveGateState.SignedOut -> PhoneOtpScreen(
            repository = repository,
            onSignedIn = { gateState = LiveGateState.SignedIn }
        )

        LiveGateState.SignedIn -> LiveProfileHost(repository = repository)
    }
}

@Composable
private fun PhoneOtpScreen(
    repository: HomiraLiveRepository,
    onSignedIn: () -> Unit
) {
    HomiraTheme {
        val scope = rememberCoroutineScope()
        var phone by remember { mutableStateOf("") }
        var otp by remember { mutableStateOf("") }
        var codeSent by remember { mutableStateOf(false) }
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
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Homira",
                    color = HomiraText,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (codeSent) {
                        "Enter the code sent to $phone"
                    } else {
                        "Sign in with your phone number"
                    },
                    color = HomiraMuted,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(28.dp))

                if (!codeSent) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it.filterNot(Char::isWhitespace) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Phone number") },
                        placeholder = { Text("+234…") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(18.dp)
                    )
                } else {
                    OutlinedTextField(
                        value = otp,
                        onValueChange = { otp = it.filter(Char::isDigit).take(6) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("6-digit code") },
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
                                    require(phone.startsWith("+") && phone.length >= 8) {
                                        "Use your full phone number with country code."
                                    }
                                    repository.sendPhoneOtp(phone)
                                    codeSent = true
                                } else {
                                    require(otp.length == 6) { "Enter the 6-digit code." }
                                    repository.verifyPhoneOtp(phone, otp)
                                    onSignedIn()
                                }
                            }.onFailure {
                                error = it.message ?: "Could not continue. Try again."
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
                    Button(
                        onClick = {
                            codeSent = false
                            otp = ""
                            error = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HomiraSurface,
                            contentColor = HomiraText
                        )
                    ) {
                        Text("Change number")
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
private fun LiveProfileHost(repository: HomiraLiveRepository) {
    var loading by remember { mutableStateOf(true) }
    var profile by remember { mutableStateOf<LiveProfile?>(null) }
    var contacts by remember { mutableStateOf<List<LiveContact>>(emptyList()) }

    LaunchedEffect(Unit) {
        profile = repository.loadMyProfile()
        contacts = runCatching { repository.loadContacts() }.getOrDefault(emptyList())
        loading = false
    }

    if (loading) {
        HomiraTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(HomiraBackground),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = HomiraGreen)
            }
        }
    } else {
        HomiraProductionApp(initialProfile = profile, initialContacts = contacts)
    }
}
