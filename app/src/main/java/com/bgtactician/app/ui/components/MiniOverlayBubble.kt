package com.bgtactician.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bgtactician.app.data.model.AutoDetectStatus

@Composable
fun MiniOverlayBubble() {
    Box(
        modifier = Modifier
            .size(width = 28.dp, height = 104.dp)
            .shadow(14.dp, RoundedCornerShape(18.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xE61C8B72), Color(0xCC0D1722))
                ),
                shape = RoundedCornerShape(18.dp)
            )
            .border(1.dp, Color(0x80F2C66D), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "B",
                color = Color(0xFFF7E2A1),
                fontSize = 16.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "G",
                color = Color(0xFFF7E2A1),
                fontSize = 16.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
fun MiniOverlayDetectChip(status: AutoDetectStatus) {
    val (background, border, text, alpha) = when (status) {
        AutoDetectStatus.WAITING -> DetectChipVisual(Color(0xD9142230), Color(0x6AF0C56D), "识别", 1f)
        AutoDetectStatus.SCANNING -> DetectChipVisual(Color(0xE6A66E12), Color(0x99FFD36B), "识别中", 0.72f)
        AutoDetectStatus.LOCKED -> DetectChipVisual(Color(0xD9156D55), Color(0x887FE7C6), "重扫", 1f)
        AutoDetectStatus.NEEDS_ATTENTION -> DetectChipVisual(Color(0xD9824429), Color(0x99FFB58E), "重试", 1f)
    }

    Box(
        modifier = Modifier
            .size(width = 54.dp, height = 28.dp)
            .graphicsLayer(alpha = alpha)
            .shadow(10.dp, RoundedCornerShape(14.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(background, background.copy(alpha = 0.82f))
                ),
                shape = RoundedCornerShape(14.dp)
            )
            .border(1.dp, border, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color(0xFFF7E2A1),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black
        )
    }
}

private data class DetectChipVisual(
    val background: Color,
    val border: Color,
    val text: String,
    val alpha: Float
)
