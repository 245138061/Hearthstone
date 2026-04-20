package com.bgtactician.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bgtactician.app.data.model.AutoDetectStatus

private val BubbleNight = Color(0xFF1A2235)
private val BubbleBoard = Color(0xFF26344C)
private val BubbleTrim = Color(0x66FFD45B)
private val BubbleGold = Color(0xFFFFD45B)
private val BubbleIvory = Color(0xFFFFF1C9)
private val BubbleMint = Color(0xFF6BE0A5)
private val BubbleWarn = Color(0xFFFFC36E)
private val BubbleDanger = Color(0xFFFF8364)

@Composable
fun MiniOverlayBubble() {
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 118.dp)
            .shadow(16.dp, RoundedCornerShape(22.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(BubbleBoard, BubbleNight)
                ),
                shape = RoundedCornerShape(22.dp)
            )
            .border(1.dp, BubbleTrim, RoundedCornerShape(22.dp))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(BubbleGold, Color(0xFFC88A26), Color(0xFF6E4A12))
                        )
                    )
                    .border(1.dp, BubbleGold.copy(alpha = 0.78f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                BubbleDoorGlyph(color = Color(0xFF2A1705))
            }

            Text(
                text = "酒",
                color = BubbleIvory,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "馆",
                color = BubbleIvory,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )

            Spacer(modifier = Modifier.height(2.dp))

            Box(
                modifier = Modifier
                    .size(width = 18.dp, height = 3.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(BubbleGold.copy(alpha = 0.56f))
            )
        }
    }
}

@Composable
fun MiniOverlayDetectChip(
    status: AutoDetectStatus,
    tavernTierLabel: String? = null
) {
    val visual = when (status) {
        AutoDetectStatus.WAITING -> DetectChipVisual("等待", BubbleWarn, 1f)
        AutoDetectStatus.SCANNING -> DetectChipVisual("识别中", BubbleGold, 0.84f)
        AutoDetectStatus.LOCKED -> DetectChipVisual("已锁定", BubbleMint, 1f)
        AutoDetectStatus.NEEDS_ATTENTION -> DetectChipVisual("重试", BubbleDanger, 1f)
    }

    Column(
        modifier = Modifier
            .widthIn(min = 52.dp)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        RowStatus(visual = visual)
        tavernTierLabel?.takeIf(String::isNotBlank)?.let {
            Text(
                text = it.substringBefore(" · "),
                color = BubbleIvory.copy(alpha = 0.88f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RowStatus(
    visual: DetectChipVisual
) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(visual.signal.copy(alpha = visual.alpha))
        )
        Text(
            text = visual.text,
            color = BubbleIvory,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

@Composable
private fun BubbleDoorGlyph(
    modifier: Modifier = Modifier.size(14.dp),
    color: Color
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.12f
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.24f, size.height * 0.18f),
            size = Size(size.width * 0.52f, size.height * 0.66f),
            cornerRadius = CornerRadius(size.width * 0.20f, size.width * 0.20f),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.50f, size.height * 0.25f),
            end = Offset(size.width * 0.50f, size.height * 0.83f),
            strokeWidth = stroke * 0.9f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = color,
            radius = stroke * 0.52f,
            center = Offset(size.width * 0.60f, size.height * 0.52f)
        )
    }
}

private data class DetectChipVisual(
    val text: String,
    val signal: Color,
    val alpha: Float
)
