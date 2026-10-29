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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pangguai.breeze.BreezeViewModel
import com.pangguai.breeze.ui.theme.Ink

/**
 * 登录页：手机号 + 短信验证码。
 *
 * 服务端链路：common/sms/sendCode {phone, template:"reg"} → user/reg {channel, phone, verify}
 * 没有 token 什么都做不了（连扫码都会被当成未登录），所以它是第一屏。
 */
@Composable
fun LoginScreen(vm: BreezeViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(96.dp))

        Box(contentAlignment = Alignment.Center) {
            WindGlyph(size = 56.dp, color = Ink.mint)
        }
        Spacer(Modifier.height(18.dp))
        Text("轻风", color = Ink.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp)
        Spacer(Modifier.height(6.dp))
        Text("B R E E Z E", color = Ink.dim2, fontSize = 10.sp, letterSpacing = 3.sp)
        Spacer(Modifier.height(10.dp))
        Text("登录后扫码即用，按次计费", color = Ink.dim, fontSize = 13.sp)

        Spacer(Modifier.height(44.dp))

        Field(
            value = vm.phoneInput,
            onValueChange = vm::onPhoneChange,
            placeholder = "手机号",
            keyboard = KeyboardType.Phone,
        )
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Field(
                    value = vm.codeInput,
                    onValueChange = vm::onCodeChange,
                    placeholder = "短信验证码",
                    keyboard = KeyboardType.Number,
                )
            }
            Spacer(Modifier.width(10.dp))
            val cooling = vm.codeCooldown > 0
            Box(
                Modifier
                    .width(104.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Ink.line, RoundedCornerShape(14.dp))
                    .clickable(enabled = !cooling && !vm.busy, onClick = vm::sendCode),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (cooling) "${vm.codeCooldown}s" else "获取验证码",
                    color = if (cooling) Ink.dim2 else Ink.mint2,
                    fontSize = 13.sp,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            text = if (vm.busy) "处理中…" else "登录",
            enabled = !vm.busy,
            onClick = vm::login,
        )

        Spacer(Modifier.height(14.dp))
        Text(
            "继续即表示同意《用户协议》与《隐私政策》",
            color = Ink.dim2,
            fontSize = 11.sp,
        )

        Spacer(Modifier.weight(1f))
        Text("本应用为个人自用工具，与运营商无关", color = Ink.dim2, fontSize = 10.sp)
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboard: KeyboardType,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.surface)
            .border(1.dp, Ink.line, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = Ink.dim2, fontSize = 15.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Ink.text, fontSize = 15.sp),
            cursorBrush = SolidColor(Ink.mint),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 登录页顶部的小风字：三笔薄荷绿弧线，用 Canvas 画，避免引图标库。 */
@Composable
private fun WindGlyph(size: androidx.compose.ui.unit.Dp, color: androidx.compose.ui.graphics.Color) {
    androidx.compose.foundation.Canvas(Modifier.height(size).width(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.075f
        val p = androidx.compose.ui.graphics.Path()
        // 上：一横 + 右端下勾
        p.moveTo(w * 0.18f, h * 0.32f)
        p.lineTo(w * 0.62f, h * 0.32f)
        p.quadraticBezierTo(w * 0.92f, h * 0.32f, w * 0.86f, h * 0.52f)
        this.drawPath(
            path = p,
            color = color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            ),
        )
        // 中：稍短一横
        val p2 = androidx.compose.ui.graphics.Path()
        p2.moveTo(w * 0.14f, h * 0.56f)
        p2.lineTo(w * 0.56f, h * 0.56f)
        this.drawPath(
            path = p2,
            color = color.copy(alpha = 0.85f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            ),
        )
        // 下：最短一横
        val p3 = androidx.compose.ui.graphics.Path()
        p3.moveTo(w * 0.22f, h * 0.78f)
        p3.lineTo(w * 0.5f, h * 0.78f)
        this.drawPath(
            path = p3,
            color = color.copy(alpha = 0.6f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            ),
        )
    }
}
