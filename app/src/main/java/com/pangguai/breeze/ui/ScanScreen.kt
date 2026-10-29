package com.pangguai.breeze.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pangguai.breeze.ui.theme.Ink

/**
 * 竖屏扫码页：整屏相机预览 + 居中的正方形取景框（微信那种），
 * 不锁横屏、不跳独立 Activity。
 */
@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onManual: () -> Unit,
    onCode: (String) -> Unit,
    onError: (String) -> Unit = {},
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {

        CameraQrScanner(onCode = onCode, onError = onError)

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val side = (maxWidth * 0.72f).coerceAtMost(maxHeight * 0.5f)

            // 取景框四周压暗（中间留一个正方形）
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(side).border(1.dp, Ink.mint.copy(alpha = 0.35f), RoundedCornerShape(8.dp)))
            }
            val scrim = Color.Black.copy(alpha = 0.62f)
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f).background(scrim))
                Row(Modifier.fillMaxWidth().height(side)) {
                    Box(Modifier.weight(1f).fillMaxSize().background(scrim))
                    Spacer(Modifier.size(side))
                    Box(Modifier.weight(1f).fillMaxSize().background(scrim))
                }
                Box(Modifier.fillMaxWidth().weight(1f).background(scrim))
            }

            // 四角 + 扫描线
            Box(Modifier.align(Alignment.Center).size(side)) {
                ScannerGlyph(side, Ink.mint)
                val tr = rememberInfiniteTransition(label = "laser")
                val y by tr.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1900, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "laserY",
                )
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = (side - 4.dp) * y)
                        .fillMaxWidth(0.9f)
                        .height(2.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Ink.mint2, Color.Transparent)
                            )
                        )
                )
            }

            // 顶部关闭按钮 + 标题
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 20.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                        .clickableNoRipple(onBack),
                    contentAlignment = Alignment.Center,
                ) { Text("✕", color = Color.White, fontSize = 15.sp) }
                Spacer(Modifier.size(12.dp))
                Text("扫一扫", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            // 底部提示 + 手动输入
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("对准设备上的二维码", color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "自动识别 · 不用按快门",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.42f))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
                        .clickableNoRipple(onManual),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("手动输入设备号", color = Color.White.copy(alpha = 0.88f), fontSize = 14.sp)
                }
            }
        }
    }
}

/** 无涟漪点击（相机预览上叠按钮时，涟漪会打断扫码头的手感）。 */
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interaction = androidx.compose.runtime.remember {
        androidx.compose.foundation.interaction.MutableInteractionSource()
    }
    return this.clickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick,
    )
}
