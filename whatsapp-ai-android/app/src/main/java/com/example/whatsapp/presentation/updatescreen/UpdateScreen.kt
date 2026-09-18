package com.example.whatsapp.presentation.updatescreen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.whatsapp.R
import com.example.whatsapp.presentation.bottomnavigation.BottomNavigationBar
import com.example.whatsapp.presentation.navigation.Routes

@Composable
fun UpdateScreen(navHostController: NavHostController) {

    val scrollState = rememberScrollState()

    val sampleStatus = listOf(
        StatusData(R.drawable.shahyan, name = "Shayan Ahmad", time = "Just Now"),
        StatusData(R.drawable.salleh, name = "Saleh Hayat", time = "10 min ago"),
        StatusData(R.drawable.haider, name = "Haider Ali", time = "1 hour ago"),
        StatusData(R.drawable.mrbeast, name = "Mr Beast", time = "10 hours ago"),
    )

    val sampleChannels = listOf(
        Channel(R.drawable.talal, name = "Talal Vines", description = "Make it for Fun"),
        Channel(R.drawable.taimoor, name = "Butt Brothers", description = "just looking Forward"),
        Channel(R.drawable.whatsapp_icon, name = "WhatsApp", description = "Know World Wide"),
        Channel(R.drawable.mrbeast, name = "Mr Beast", description = "Just Watching")
    )



    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /*TODO*/ },
                containerColor = colorResource(id = R.color.light_green),
                modifier = Modifier.size(size = 60.dp),
                contentColor = Color.White
            ) {
                Icon(

                    painter = painterResource(id = R.drawable.camera),
                    contentDescription = null,
                    modifier = Modifier.size(size = 28.dp)
                )

            }


        },

        bottomBar = {
            BottomNavigationBar(
                navHostController = navHostController,
                selectedItem = 1,
                onClick = { index ->
                    when (index) {
                        0 -> {
                            if (navHostController.currentDestination?.route != Routes.HomeScreen.route) {
                                navHostController.navigate(Routes.HomeScreen.route) {
                                    popUpTo(Routes.HomeScreen.route) { inclusive = true }
                                }
                            }
                        }
                        1 -> {
                            if (navHostController.currentDestination?.route != Routes.UpdateScreen.route) {
                                navHostController.navigate(Routes.UpdateScreen.route)
                            }
                        }
                        2 -> {
                            if (navHostController.currentDestination?.route != Routes.CommunitiesScreen.route) {
                                navHostController.navigate(Routes.CommunitiesScreen.route)
                            }
                        }
                        3 -> {
                            if (navHostController.currentDestination?.route != Routes.CallScreen.route) {
                                navHostController.navigate(Routes.CallScreen.route)
                            }
                        }
                    }
                }
            )
        },

        topBar = {
            TopBar()
        }

    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {

            Text(
                text = "Status",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color.Black,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )



            MyStatus()

            sampleStatus.forEach {
                StatusItem(statusData = it)
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(color = Color.Gray)

            Text(
                text = "Channels",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color.Black,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {

                Text(
                    text = "Stay Update on topic that matter to you. Find channels to follow below"
                )
                Spacer(modifier = Modifier.height(26.dp))
                Text(text = "Find Channels to follow")

            }
            Spacer(modifier = Modifier.height(16.dp))

            sampleChannels.forEach {
                ChannelItemDesign(channel = it)
            }


        }
    }

}