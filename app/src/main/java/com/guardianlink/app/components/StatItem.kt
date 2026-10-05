package com.guardianlink.app.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StatItem(
    title: String,
    value: String
) {

    Card(
        modifier = Modifier
            .width(160.dp)
            .height(100.dp),
        shape = RoundedCornerShape(20.dp)
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = title
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
        }
    }
}