package com.pangguai.breeze.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pangguai.breeze.BreezeViewModel
import com.pangguai.breeze.ui.theme.Ink

@Composable
fun HomeScreen(
    vm: BreezeViewModel,
    onScan: () -> Unit,
    onManual: () -> Unit,
    onHistory: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        // 顶部状态
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("● " + vm.phone, fg = Ink.text2, borderColor = Ink.line)
                Spacer(Modifier.width(8.dp))
                Text(
                    "退出",
                    color = Ink.dim,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { vm.logout() },
                )
            }
        }

        // 品牌
        Row(Modifier.padding(top = 26.dp), verticalAlignment = Alignment.CenterVertically) {
            WindGlyph(width = 30.dp, height = 24.dp, color = Ink.mint2)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "轻风",
                    color = Ink.text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 4.sp,
                )
                Text("BREEZE · 一键吹风", color = Ink.dim2, fontSize = 10.sp, letterSpacing = 3.sp)
            }
        }

        // 主操作
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            BigScanButton(onScan)
        }

        Row(Modifier.fillMaxWidth()) {
            GhostButton("手动输入设备号", Modifier.weight(1f), onManual)
            Spacer(Modifier.width(10.dp))
            GhostButton("历史订单", Modifier.weight(1f), onHistory)
        }
        Spacer(Modifier.height(10.dp))

        if (vm.orderNo.isNotBlank()) {
            LastOrderCard(vm)
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun BigScanButton(onClick: () -> Unit) {
    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(260.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Ink.mint.copy(alpha = 0.30f), Color.Transparent)
                    ),
                    CircleShape,
                )
        )
        Box(
            Modifier
                .size(206.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Ink.mint.copy(alpha = 0.98f), Color(0xFF1D9C71)),
                    )
                )
                .border(1.dp, Ink.mint2.copy(alpha = 0.55f), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ScannerGlyph(44.dp, Ink.onMint)
                Spacer(Modifier.height(10.dp))
                Text(
                    "扫码吹风",
                    color = Ink.onMint,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 3.sp,
                )
            }
        }
    }
}

@Composable
private fun LastOrderCard(vm: BreezeViewModel) {
    BreezeCard(Modifier.fillMaxWidth(), radius = 16.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Ink.surface2)
                    .border(1.dp, Ink.line, CircleShape),
                contentAlignment = Alignment.Center,
            ) { WindGlyph(16.dp, 12.dp, Ink.mint2) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(vm.device.let { it.name + (if (it.area.isBlank()) "" else " · " + it.area) }, color = Ink.text, fontSize = 14.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${vm.plan.minutes} 分钟 · 最近一次",
                    color = Ink.dim2,
                    fontSize = 11.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(vm.plan.priceText, color = Ink.mint2, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
    Spacer(Modifier.height(0.dp))
}
