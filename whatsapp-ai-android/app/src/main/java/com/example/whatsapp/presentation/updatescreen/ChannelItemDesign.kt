package com.example.whatsapp.presentation.updatescreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.R

@Composable

fun ChannelItemDesign(channel: Channel) {

    var isFollowing by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Image(
            painter = painterResource(channel.image),
            contentDescription = null,
            modifier = Modifier
                .size(60.dp)
                .padding(4.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column {

            Text(text = channel.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(text = channel.description, fontSize = 14.sp, color = Color.Gray)
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { isFollowing = !isFollowing },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isFollowing) Color.Gray else colorResource(id = R.color.light_green)
            ),
            modifier = Modifier.padding(8.dp).height(36.dp)
        ) {
            Text(
                text = if (isFollowing) "Following" else "Follow",
                color =if (isFollowing) Color.Black else Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

data class Channel(val image:Int, val name:String, val description:String)
