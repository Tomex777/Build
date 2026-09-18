package com.example.whatsapp.presentation.updatescreen


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.whatsapp.R

@Composable
@Preview(showBackground = true, showSystemUi = true)
fun TopBar() {
    var inSearching by remember {
        mutableStateOf(false)
    }

    var search by remember {
        mutableStateOf("")
    }

    var showMany by remember {
        mutableStateOf(false)
    }


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
                        text = "Updates",
                        fontSize = 28.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 12.dp, top = 12.dp)
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
                            modifier = Modifier.size(15.dp)

                        )
                    }
                } else {
                    IconButton(onClick = { /*TODO*/ }) {
                        Icon(
                            painter = painterResource(id = R.drawable.camera),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )

                    }

                    IconButton(onClick = { inSearching = true }) {
                        Icon(
                            painter = painterResource(id = R.drawable.search),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )

                    }

                    // 👇 Dropdown Menu Button
                    Box (modifier = Modifier.zIndex(1f)){
                        IconButton(onClick = { showMany = !showMany }) {
                            Icon(
                                painter = painterResource(id = R.drawable.more),
                                contentDescription = "More Options",
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMany,
                            onDismissRequest = { showMany = false },
                            
                        ) {
                            DropdownMenuItem(
                                text = { Text("Privacy Policy") },
                                onClick = { showMany = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Create Chennals") },
                                onClick = { showMany = false }
                            )

                        }
                    }

                }
            }

            HorizontalDivider()
        }

    }


}