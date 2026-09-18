package com.example.whatsapp.presentation.welcomescreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.navigation.NavHostController
import com.example.whatsapp.presentation.navigation.Routes


@Composable
fun WelcomeScreen(navHostController: NavHostController) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.whatsapp_sticker),
                contentDescription = null,
                modifier = Modifier.size(300.dp)
            )

            Text(
                text = "Welcome To WhatsApp",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(height = 20.dp))

            Text(
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = Color.Gray)) {
                        append("Read our ")
                    }
                    withStyle(style = SpanStyle(color = Color(0xFF25D366))) {   // light green
                        append("Privacy Policy")
                    }
                    withStyle(style = SpanStyle(color = Color.Gray)) {
                        append(" and tap 'Agree and Continue' to accept the ")
                    }
                    withStyle(style = SpanStyle(color = Color(0xFF25D366))) {
                        append("Terms and Conditions.")
                    }
                },
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp)
            )


            Spacer(modifier = Modifier.height(height = 20.dp))

            Button(
                onClick = { navHostController.navigate(Routes.UserRegistrationScreen.route) },
                modifier = Modifier
                    .width(260.dp)
                    .height(43.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorResource(id = R.color.dark_green)
                )
            )
            {
                Text(text = "Agree and Continue", color = Color.White)
            }


        }
    }
}
