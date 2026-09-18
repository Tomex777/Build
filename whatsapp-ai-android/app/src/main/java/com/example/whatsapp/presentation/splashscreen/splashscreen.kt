package com.example.whatsapp.presentation.splashscreen


import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.whatsapp.R
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.colorResource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import com.example.whatsapp.presentation.navigation.Routes
import kotlinx.coroutines.delay
import androidx.navigation.compose.rememberNavController
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.auth.FirebaseAuth

@Composable
fun SplashScreen(navHostController: NavHostController) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val isSignedIn = sharedPreferences.getBoolean("isSignedIn", false)
    val currentUser = FirebaseAuth.getInstance().currentUser

    LaunchedEffect(Unit) {
        delay(1000)
        
        // Check if user is already logged in
        if (isSignedIn && currentUser != null) {
            // User is logged in, go to Home screen
            navHostController.navigate(Routes.HomeScreen.route) {
                popUpTo(Routes.SplashScreen.route) {
                    inclusive = true
                }
            }
        } else {
            // User is not logged in, go to Welcome screen
            navHostController.navigate(Routes.WelcomeScreen.route) {
                popUpTo(Routes.SplashScreen.route) {
                    inclusive = true
                }
            }
        }
    }


    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.whatsapp_icon),
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .align(Alignment.Center) // 👈 centers the image
        )

        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "From",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Row {
                Icon(
                    painter = painterResource(id = R.drawable.meta),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = colorResource(id = R.color.light_green) // ✅ Correct
                )
                Spacer(modifier = Modifier.width(5.dp)) // 👈 adds space between icon and text
                Text(
                    text = "Meta",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

            }
            Spacer(modifier = Modifier.padding (bottom = 40.dp))

        }


    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun SplashScreenPreview() {
    val navController = rememberNavController() // ✅ create a fake NavController
    SplashScreen(navHostController = navController)
}