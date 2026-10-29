package com.pangguai.breeze.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 扫码取景框的四个角 */
@Composable
fun ScannerGlyph(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val len = s * 0.26f
        val sw = s * 0.075f
        val pad = sw / 2f
        // 左上
        drawLine(color, Offset(pad, pad + len), Offset(pad, pad), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(pad, pad), Offset(pad + len, pad), strokeWidth = sw, cap = StrokeCap.Round)
        // 右上
        drawLine(color, Offset(s - pad - len, pad), Offset(s - pad, pad), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(s - pad, pad), Offset(s - pad, pad + len), strokeWidth = sw, cap = StrokeCap.Round)
        // 左下
        drawLine(color, Offset(pad, s - pad - len), Offset(pad, s - pad), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(pad, s - pad), Offset(pad + len, s - pad), strokeWidth = sw, cap = StrokeCap.Round)
        // 右下
        drawLine(color, Offset(s - pad - len, s - pad), Offset(s - pad, s - pad), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(s - pad, s - pad - len), Offset(s - pad, s - pad), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

/** 三根飘线 = 风 */
@Composable
fun WindGlyph(width: Dp, height: Dp, color: Color) {
    Canvas(Modifier.size(width = width, height = height)) {
        val w = this.size.width
        val h = this.size.height
        val sw = h * 0.055f
        val rows = listOf(0.26f, 0.5f, 0.74f)
        rows.forEachIndexed { i, fy ->
            val span = if (i == 2) w * 0.62f else w * 0.92f
            val startX = w * 0.04f
            val y = h * fy
            val p = Path().apply {
                moveTo(startX, y)
                val seg = span / 6f
                for (k in 0 until 5) {
                    val x0 = startX + seg * k
                    val y0 = y + (if (k % 2 == 0) -h * 0.03f else h * 0.03f)
                    val x1 = startX + seg * (k + 1)
                    val y1 = y + (if (k % 2 == 0) h * 0.03f else -h * 0.03f)
                    quadraticBezierTo(x0 + seg / 2f, y0, x1, y1)
                }
                // 末端上翘
                quadraticBezierTo(startX + span + w * 0.05f, y - h * 0.09f, startX + span + w * 0.05f, y - h * 0.16f)
            }
            drawPath(p, color = color, style = Stroke(width = sw, cap = StrokeCap.Round))
        }
    }
}

@Composable
fun Dot(color: Color, size: Dp = 6.dp) {
    Canvas(Modifier.size(size)) { drawCircle(color) }
}

@Composable
fun CheckTick(color: Color, size: Dp = 14.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val p = Path().apply {
            moveTo(s * 0.18f, s * 0.52f)
            lineTo(s * 0.42f, s * 0.76f)
            lineTo(s * 0.84f, s * 0.24f)
        }
        drawPath(p, color, style = Stroke(width = s * 0.14f, cap = StrokeCap.Round))
    }
}
