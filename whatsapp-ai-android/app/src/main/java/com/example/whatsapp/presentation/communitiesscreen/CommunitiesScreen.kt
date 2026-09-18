package com.example.whatsapp.presentation.communitiesscreen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import com.example.whatsapp.R
import com.example.whatsapp.presentation.bottomnavigation.BottomNavigationBar
import com.example.whatsapp.presentation.navigation.Routes

@Composable
fun CommunitiesScreen(navHostController: NavHostController) {

    var inSearching by remember {
        mutableStateOf(false)
    }

    var search by remember {
        mutableStateOf("")
    }

    var showMany by remember {
        mutableStateOf(false)
    }

    val sampleCommunities = listOf(
        Communities(image = R.drawable.img, name = "Tech Enthusiasts", membercount = "200k member"),
        Communities(image = R.drawable.img, name = "Photography Lovers", membercount = "1M member"),
        Communities(image = R.drawable.img, name = "Music Fans", membercount = "500k member"),
        Communities(image = R.drawable.img, name = "Travel Explorers", membercount = "300k member"),

    )



    Scaffold(
        topBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Spacer(modifier = Modifier.height(height = 16.dp))

                    Row {

                        if (inSearching) {
                            TextField(
                                value = search, onValueChange = {
                                    search = it
                                }, placeholder = {
                                    Text(text = "Search")

                                }, colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ), modifier = Modifier.padding(start = 12.dp), singleLine = true
                            )
                        } else {
                            Text(
                                text = "Communities",
                                fontSize = 28.sp,
                                color = Color.Black,
                                modifier = Modifier.padding(start = 12.dp, top = 12.dp),
                            )


                        }
                        Spacer(modifier = Modifier.weight(1f))

                        if (inSearching) {
                            IconButton(onClick = {
                                inSearching = false
                                search = ""
                            }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.cross),
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),

                                )
                            }
                        } else {

                            IconButton(onClick = { inSearching = true }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.search),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )

                            }

                            // 👇 Dropdown Menu Button
                            Box(modifier = Modifier.zIndex(1f)) {
                                IconButton(onClick = { showMany = !showMany }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.more),
                                        contentDescription = "More Options",
                                        modifier = Modifier.size(24.dp),
                                    )
                                }

                                DropdownMenu(
                                    expanded = showMany,
                                    onDismissRequest = { showMany = false },

                                    ) {
                                    DropdownMenuItem(
                                        text = { Text("Setting") },
                                        onClick = { showMany = false }
                                    )
                                }
                            }

                        }
                    }

                    HorizontalDivider()
                }

            }
        },
        bottomBar = {
            BottomNavigationBar(
                navHostController = navHostController,
                selectedItem = 2,
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
        }) {
        Column(modifier = Modifier.padding(it)) {

            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = colorResource(id = R.color.light_green)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(text = "Start a new community", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Popular Communities",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            LazyColumn {

                items(sampleCommunities){
                    CommunitiesItemsDesign(communities = it)
                }

            }

        }

    }
}