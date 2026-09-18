package com.example.whatsapp.presentation.callscreen

import com.example.whatsapp.R
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
@Preview(showBackground = true, showSystemUi = true)
fun FavoritesSection() {

    val simpleFavorites = listOf(
        FavoritesContact(image = R.drawable.waleed, name = "Waleed"),
        FavoritesContact(image = R.drawable.bilal, name = "Bilal"),
        FavoritesContact(image = R.drawable.jazib_asad, name = "JaZiB Asad"),
        FavoritesContact(image = R.drawable.abdussalam, name = "AbduS Salam"),
        FavoritesContact(image = R.drawable.salman_khan, name = "Salman Khan"),
        FavoritesContact(image = R.drawable.harib, name = "Harib")

    )

    Column(modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)) {

        Text(
            text = "Favorites",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())) {

            simpleFavorites.forEach {
                FavoritesItems(it)
            }
        }
    }
}

data class FavoritesContact(
    val image: Int,
    val name: String
)