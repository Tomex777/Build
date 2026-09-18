package com.example.whatsapp.presentation.userregistrationscreen

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import com.example.whatsapp.R
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.whatsapp.presentation.navigation.Routes
import com.example.whatsapp.presentation.viewmodels.AuthState
import com.example.whatsapp.presentation.viewmodels.PhoneAuthViewModel


@Composable
fun PreviewUserRegistrationScreen() {
    UserRegistrationScreen(navController = rememberNavController())
}

@Composable
fun UserRegistrationScreen(
    navController: NavHostController,
    phoneAuthViewModel: PhoneAuthViewModel = hiltViewModel()
) {
    val state by phoneAuthViewModel.authState.collectAsState()   // ✅ sirf ek state
    val context = LocalContext.current
    val activity = LocalActivity.current as Activity

    var otp by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var selectedCountry by remember { mutableStateOf("Pakistan") }
    var phoneNumber by remember { mutableStateOf("") }
    var countryCode by remember { mutableStateOf("+92") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.White)
            .padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Enter Your Phone Number",
            fontSize = 20.sp,
            color = colorResource(id = R.color.dark_green),
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "WhatsApp needs to verify your phone number",
            modifier = Modifier.fillMaxWidth(),
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "What's ",
                color = colorResource(id = R.color.light_green)
            )
            Text(
                text = "Your Number",
                color = colorResource(id = R.color.light_green)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = selectedCountry,
                    fontSize = 16.sp,
                    color = colorResource(id = R.color.black)
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = colorResource(id = R.color.light_green),
                    modifier = Modifier
                        .padding(start = 30.dp)
                        .size(20.dp)
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 66.dp),
            thickness = 3.dp,
            color = colorResource(id = R.color.light_green)
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            listOf(
                "Pakistan", "India", "Bangladesh", "Afghanistan",
                "Nepal", "Sri Lanka", "Bhutan", "Myanmar", "China"
            ).forEach { country ->
                DropdownMenuItem(
                    text = { Text(text = country) },
                    onClick = {
                        selectedCountry = country
                        expanded = false
                    }
                )
            }
        }

        // ✅ Yahan se sirf ek when(state)
        when (state) {

            is AuthState.Ideal -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = countryCode,
                        onValueChange = { countryCode = it },
                        modifier = Modifier.width(70.dp),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = colorResource(R.color.light_green)
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    TextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (phoneNumber.isNotEmpty()) {
                            val fullPhoneNumber = "$countryCode$phoneNumber"
                            phoneAuthViewModel.sendVerificationCode(fullPhoneNumber, activity)
                        } else {
                            Toast.makeText(
                                context,
                                "Please enter a valid phone number",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(colorResource(R.color.dark_green))
                ) {
                    Text(text = "Send OTP")
                }
            }

            is AuthState.Loading -> {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            }

            is AuthState.CodeSent -> {
                Spacer(modifier = Modifier.height(40.dp))

                Text(
                    text = "Enter OTP",
                    fontSize = 20.sp,
                    color = colorResource(id = R.color.dark_green),
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    value = otp,
                    onValueChange = { otp = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("OTP") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent
                    )
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        if (otp.isNotEmpty()) {
                            phoneAuthViewModel.verifyCode(otp, context)
                        } else {
                            Toast.makeText(
                                context,
                                "Please enter a valid OTP",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(colorResource(R.color.dark_green))
                ) {
                    Text(text = "Verify OTP")
                }
            }



            is AuthState.Success -> {
                LaunchedEffect(Unit) {
                    phoneAuthViewModel.resetAuthState()
                    navController.navigate(Routes.UserProfileSetScreen.route) {
                        popUpTo(Routes.UserRegistrationScreen.route) { inclusive = true }
                    }
                }

                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()  // Optional: loading dikhao
                }
            }


            is AuthState.Error -> {
                Toast.makeText(
                    context,
                    (state as AuthState.Error).message,
                    Toast.LENGTH_SHORT
                ).show()
                phoneAuthViewModel.resetAuthState()
            }
        }
    }
}
