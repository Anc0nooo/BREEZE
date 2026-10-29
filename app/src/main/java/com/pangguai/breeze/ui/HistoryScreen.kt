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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pangguai.breeze.BreezeViewModel
import com.pangguai.breeze.data.OrderRecord
import com.pangguai.breeze.ui.theme.Ink

/**
 * 历史订单：order/list 的最近 20 条。
 * 状态文案与官方 App 一致（0 未支付 / 1 支付超时 / 2 已支付 / 3 已完成 / 4 退款中 / 5 已退款）。
 */
@Composable
fun HistoryScreen(vm: BreezeViewModel, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = "历史订单",
            onBack = onBack,
            trailing = {
                Text(
                    "刷新",
                    color = Ink.mint2,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { vm.loadOrders() },
                )
            },
        )

        when {
            vm.ordersBusy && vm.orders.isEmpty() -> Center { CircularProgressIndicator(color = Ink.mint) }
            vm.orders.isEmpty() -> Center {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    WindGlyph(width = 40.dp, height = 32.dp, color = Ink.line)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (vm.ordersLoaded) "还没有订单" else "正在加载…",
                        color = Ink.dim2,
                        fontSize = 13.sp,
                    )
                }
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 不用 orderNo 当 key：服务端理论上不会重复，但重复 key 会让 LazyColumn 直接崩
                items(vm.orders.size) { i -> OrderCard(vm.orders[i]) }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun OrderCard(o: OrderRecord) {
    BreezeCard(Modifier.fillMaxWidth(), radius = 16.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Ink.surface2)
                            .border(1.dp, Ink.line, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { WindGlyph(14.dp, 11.dp, Ink.mint2) }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(o.machineName, color = Ink.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            listOf(o.shopName, o.createTime).filter { it.isNotBlank() }.joinToString(" · "),
                            color = Ink.dim2,
                            fontSize = 10.sp,
                        )
                    }
                }
                Pill(
                    o.statusText,
                    fg = if (o.isDone) Ink.mint2 else Ink.dim,
                    borderColor = if (o.isDone) Ink.mint.copy(alpha = 0.45f) else Ink.line,
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.line))
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(o.shortNo, color = Ink.dim2, fontSize = 10.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (o.minutes > 0) "${o.minutes} 分钟" else "—",
                        color = Ink.text2,
                        fontSize = 12.sp,
                    )
                }
                Text(o.priceText, color = Ink.mint2, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
