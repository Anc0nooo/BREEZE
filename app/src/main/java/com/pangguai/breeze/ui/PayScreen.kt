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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pangguai.breeze.BreezeViewModel
import com.pangguai.breeze.data.PayChannel
import com.pangguai.breeze.data.Plan
import com.pangguai.breeze.data.formatFen
import com.pangguai.breeze.ui.theme.Ink

@Composable
fun PayScreen(vm: BreezeViewModel, onBack: () -> Unit, onPay: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar(title = "确认订单", onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // 设备
            BreezeCard(Modifier.fillMaxWidth(), radius = 16.dp) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Ink.surface2)
                            .border(1.dp, Ink.line, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) { WindGlyph(18.dp, 14.dp, Ink.mint2) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            vm.device.name + (if (vm.device.area.isBlank()) "" else " · " + vm.device.area),
                            color = Ink.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(if (vm.device.online) Ink.mint else Ink.warn)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(vm.device.status, color = Ink.dim2, fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("选择时长")
            Spacer(Modifier.height(10.dp))

            vm.plans.forEachIndexed { i, p ->
                PlanRow(p, selected = i == vm.planIdx) { vm.choosePlan(i) }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(12.dp))
            SectionLabel("支付方式 · 以后端返回为准")
            Spacer(Modifier.height(6.dp))

            vm.channels.forEachIndexed { i, c ->
                ChannelRow(c, selected = i == vm.channelIdx) { vm.chooseChannel(i) }
                if (i != vm.channels.lastIndex) {
                    HorizontalDivider(color = Ink.line2, thickness = 1.dp)
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // 底部结算
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text("合计", color = Ink.dim, fontSize = 11.sp)
                    Spacer(Modifier.height(3.dp))
                    Text("${vm.plan.minutes} 分钟 · 实付以服务端为准", color = Ink.dim2, fontSize = 11.sp)
                }
                Text(
                    formatFen(vm.amountFen),
                    color = Ink.mint2,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            PrimaryButton(text = "立即支付", enabled = !vm.busy, onClick = onPay)
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun PlanRow(plan: Plan, selected: Boolean, onClick: () -> Unit) {
    BreezeCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        radius = 14.dp,
        color = if (selected) Ink.surface2 else Ink.surface,
        border = if (selected) Ink.mint.copy(alpha = 0.55f) else Ink.line,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioDot(selected)
            Spacer(Modifier.width(12.dp))
            Text(
                "${plan.minutes} 分钟",
                color = Ink.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            val badge = plan.badge
            if (badge != null) {
                Spacer(Modifier.width(8.dp))
                Pill(
                    badge,
                    fg = Ink.onMint,
                    bg = Ink.mint.copy(alpha = 0.85f),
                    borderColor = Color.Transparent,
                    fontSize = 10,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(plan.priceText, color = Ink.mint2, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ChannelRow(channel: PayChannel, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = channel.enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (channel.enabled) Ink.mint else Ink.dim2)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                channel.name,
                color = if (channel.enabled) Ink.text else Ink.dim,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(channel.sub, color = Ink.dim2, fontSize = 11.sp)
        }
        val tag = channel.tag
        if (channel.recommended) {
            Pill("推荐", fg = Ink.onMint, bg = Ink.mint.copy(alpha = 0.85f), borderColor = Color.Transparent, fontSize = 10)
        } else if (tag != null) {
            Pill(
                tag,
                fg = Ink.warn,
                borderColor = Ink.warn.copy(alpha = 0.45f),
                bg = Ink.warn.copy(alpha = 0.10f),
                fontSize = 10,
            )
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text("✓", color = Ink.mint, fontSize = 13.sp)
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    Box(
        Modifier
            .size(18.dp)
            .clip(CircleShape)
            .border(1.5.dp, if (selected) Ink.mint else Ink.line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(Ink.mint))
        }
    }
}
