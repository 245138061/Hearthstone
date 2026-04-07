package com.bgtactician.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MiniOverlayBubble() {
    Box(
        modifier = Modifier
            .size(68.dp)
            .shadow(18.dp, CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1C8B72), Color(0xFF0D1722))
                ),
                shape = CircleShape
            )
            .border(1.dp, Color(0x99F2C66D), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "BG",
            color = Color(0xFFF7E2A1),
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )
    }
}
