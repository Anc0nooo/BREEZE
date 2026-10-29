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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pangguai.breeze.BreezeViewModel
import com.pangguai.breeze.data.StepPhase
import com.pangguai.breeze.ui.theme.Ink

@Composable
fun DoneScreen(vm: BreezeViewModel, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = if (vm.running) "吹风中" else "处理中",
            onBack = null,
            trailing = {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .border(1.dp, Ink.line, CircleShape)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Text("✕", color = Ink.text2, fontSize = 13.sp) }
            },
        )
        if (vm.running) RunningBody(vm, onClose) else StepsBody(vm)
    }
}

@Composable
private fun StepsBody(vm: BreezeViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
        vm.steps.forEachIndexed { i, step ->
            Row {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    StepDot(step.phase)
                    if (i != vm.steps.lastIndex) {
                        Box(
                            Modifier
                                .width(1.dp)
                                .height(46.dp)
                                .background(Ink.line)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.padding(bottom = if (i == vm.steps.lastIndex) 0.dp else 18.dp)) {
                    Text(
                        step.title,
                        color = if (step.phase == StepPhase.WAIT) Ink.dim2 else Ink.text,
                        fontSize = 15.sp,
                        fontWeight = if (step.phase == StepPhase.DONE) FontWeight.Medium else FontWeight.Normal,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        step.sub,
                        color = if (step.phase == StepPhase.DOING) Ink.mint2 else Ink.dim2,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepDot(phase: StepPhase) {
    when (phase) {
        StepPhase.DONE -> Box(
            Modifier.size(22.dp).clip(CircleShape).background(Ink.mint),
            contentAlignment = Alignment.Center,
        ) { CheckTick(Ink.onMint, 12.dp) }

        StepPhase.DOING -> Box(
            Modifier.size(22.dp).clip(CircleShape).border(1.5.dp, Ink.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Ink.mint2))
        }

        StepPhase.WAIT -> Box(
            Modifier.size(22.dp).clip(CircleShape).border(1.5.dp, Ink.line, CircleShape)
        )
    }
}

@Composable
private fun RunningBody(vm: BreezeViewModel, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            WindGlyph(150.dp, 66.dp, Ink.mint2)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            formatTime(vm.remain),
            color = Ink.text,
            fontSize = 62.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            "剩余吹风时间",
            color = Ink.dim2,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(26.dp))

        // 进度
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Ink.line)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(vm.progress.coerceIn(0.02f, 1f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Ink.mint)
            )
        }
        Spacer(Modifier.height(22.dp))

        // 信息网格
        Row(Modifier.fillMaxWidth()) {
            InfoCell("设备", vm.device.name + if (vm.device.area.isBlank()) "" else " · " + vm.device.area, Modifier.weight(1f))
            InfoCell("时长", "${vm.plan.minutes} 分钟", Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            InfoCell("订单号", "NO." + vm.orderNo.takeLast(8), Modifier.weight(1f))
            InfoCell("金额", vm.plan.priceText, Modifier.weight(1f))
        }

        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, Ink.line, RoundedCornerShape(14.dp))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Text("结束吹风", color = Ink.text2, fontSize = 14.sp) }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun InfoCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Ink.dim2, fontSize = 10.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = Ink.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

private fun formatTime(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return "%02d:%02d".format(m, s)
}
