package com.night.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A private new tab never shows normal history, Journeys or recent pages. */
@Composable
internal fun PrivateStartPage() {
    Column(
        modifier = Modifier.fillMaxSize()
            .background(Color(0xFF0C1017))
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.Lock,
            contentDescription = null,
            tint = Color(0xFFB9BEFF),
            modifier = Modifier
                .background(Color(0xFF171D27), RoundedCornerShape(22.dp))
                .padding(22.dp)
                .size(34.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "Private browsing",
            color = Color(0xFFF3F5F9),
            fontSize = 23.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Private tabs aren't recorded in Journeys or restored after restart.",
            color = Color(0xFF8F9BAC),
            fontSize = 13.sp,
            lineHeight = 20.sp
        )
    }
}
