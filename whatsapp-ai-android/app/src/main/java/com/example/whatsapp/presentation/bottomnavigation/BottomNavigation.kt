package com.example.whatsapp.presentation.bottomnavigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.whatsapp.R

@Composable
fun BottomNavigationBar(

    navHostController: NavHostController,
    onClick: (index: Int) -> Unit,
    selectedItem: Int
) {

    val items = listOf(

        NavigationItem("Chats", R.drawable.chat_icon, R.drawable.outline_chat_24),
        NavigationItem("Updates", R.drawable.update_icon, R.drawable.update_icon),
        NavigationItem("Communities", R.drawable.baseline_groups_24, R.drawable.outline_groups_24),
        NavigationItem("Calls", R.drawable.telephone, R.drawable.outline_phone_24)

    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(70.dp)
    ) {

        items.forEachIndexed { index, item ->

            NavigationBarItem(
                selected = selectedItem == index,
                onClick = { onClick(index) },
                label = {
                    if (index == selectedItem) {
                        Text(
                            text = item.name,
                            color = colorResource(R.color.light_green),
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(text = item.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                icon = {
                    Icon(
                        painter = if (index == selectedItem) {
                            painterResource(item.selectedIcon)
                        } else {
                            painterResource(item.unselectedIcon)
                        },
                        contentDescription = null,
                        tint = if (index == selectedItem) {
                            colorResource(R.color.light_green)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = colorResource(R.color.mint_green),
                    selectedIconColor = colorResource(R.color.light_green),
                    selectedTextColor = colorResource(R.color.light_green)
                )
            )
        }

    }

}

data class NavigationItem(

    val name: String,
    @DrawableRes val selectedIcon: Int,
    @DrawableRes val unselectedIcon: Int
)