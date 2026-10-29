package com.pangguai.breeze.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pangguai.breeze.ui.theme.Ink

/** 卡片：surface + 1dp 描边 */
@Composable
fun BreezeCard(
    modifier: Modifier = Modifier,
    radius: Dp = 16.dp,
    color: Color = Ink.surface,
    border: Color = Ink.line,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(color)
            .border(BorderStroke(1.dp, border), RoundedCornerShape(radius))
    ) { content() }
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    fg: Color = Ink.dim,
    bg: Color = Color.Transparent,
    borderColor: Color = Ink.line,
    fontSize: Int = 11,
) {
    Text(
        text = text,
        color = fg,
        fontSize = fontSize.sp,
        maxLines = 1,
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .border(BorderStroke(1.dp, borderColor), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** 主按钮：薄荷绿实心 */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val bg = if (enabled) Ink.mint else Ink.mintDeep
    val fg = if (enabled) Ink.onMint else Ink.dim
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
    }
}

/** 次按钮：描边 */
@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, Ink.line), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Ink.text2, fontSize = 14.sp)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Ink.dim2, fontSize = 11.sp, letterSpacing = 1.sp, modifier = modifier)
}

@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(44.dp), contentAlignment = Alignment.CenterStart) {
            if (onBack != null) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .border(BorderStroke(1.dp, Ink.line), CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = Ink.text, fontSize = 20.sp) }
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(title, color = Ink.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.width(44.dp), contentAlignment = Alignment.CenterEnd) { trailing?.invoke() }
    }
}

/** 居中副标题小字 */
@Composable
fun DimText(text: String, size: Int = 12, modifier: Modifier = Modifier) {
    Text(text, color = Ink.dim2, fontSize = size.sp, modifier = modifier)
}

@Composable
fun RowHint(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(Ink.mint2)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = Ink.dim, fontSize = 13.sp)
    }
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun HRow(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
fun SpaceBetween(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, content = content)
}
