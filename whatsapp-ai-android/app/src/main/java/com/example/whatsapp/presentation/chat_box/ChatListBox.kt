package com.example.whatsapp.presentation.chat_box

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberImagePainter
import com.example.whatsapp.R
import com.example.whatsapp.presentation.viewmodels.BaseViewModel

// Helper function to get drawable resource ID for a contact name
private fun getProfileImageDrawable(contactName: String?): Int? {
    if (contactName == null) return null
    
    return when {
        contactName.contains("Ahmad", ignoreCase = true) -> R.drawable.bilal
        contactName.contains("Harib", ignoreCase = true) -> R.drawable.harib
        contactName.contains("Taimoor", ignoreCase = true) -> R.drawable.taimoor
        contactName.contains("Salam", ignoreCase = true) -> R.drawable.abdussalam
        else -> null
    }
}


@Composable
fun ChatListBox(
    chatListModel: ChatListModel,
    onClick:()-> Unit,
    baseViewModel: BaseViewModel
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        val profileImage = chatListModel.profileImage

        val bitmap = remember{
            profileImage?.let {baseViewModel.base64ToBitmap(it)}
        }
        
        // Get profile picture drawable resource based on contact name
        val profileDrawable = remember(chatListModel.name) {
            getProfileImageDrawable(chatListModel.name)
        }

        Image(
            painter = when {
                bitmap != null -> rememberImagePainter(bitmap)
                profileDrawable != null -> painterResource(profileDrawable)
                else -> painterResource(R.drawable.profile_placeholder)
            },
            contentDescription = null,
            modifier = Modifier
                .size(60.dp)
                .background(color = Color.Gray)
                .clip(shape = CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column {

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = chatListModel.name?:"Unknown",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = chatListModel.time?:"--:--",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = chatListModel.message?:"message",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

}
