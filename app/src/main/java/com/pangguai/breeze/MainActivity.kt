package com.pangguai.breeze

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pangguai.breeze.data.Screen
import com.pangguai.breeze.ui.DoneScreen
import com.pangguai.breeze.ui.HistoryScreen
import com.pangguai.breeze.ui.HomeScreen
import com.pangguai.breeze.ui.LoginScreen
import com.pangguai.breeze.ui.PayScreen
import com.pangguai.breeze.ui.ScanScreen
import com.pangguai.breeze.ui.WindGlyph
import com.pangguai.breeze.ui.theme.BreezeTheme
import com.pangguai.breeze.ui.theme.Ink

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BreezeTheme {
                // 全面屏（edge-to-edge）下必须自己吃掉状态栏/手势条的高度，
                // 否则页面顶部的胶囊和返回键会被状态栏盖住、点不到。
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Ink.bg)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    BreezeRoot()
                }
            }
        }
    }
}

@Composable
fun BreezeRoot(vm: BreezeViewModel = viewModel()) {
    val ctx = LocalContext.current
    var manualInput by remember { mutableStateOf(false) }
    var manualText by remember { mutableStateOf("") }

    LaunchedEffect(vm.toast) {
        vm.toast?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
            vm.toast = null
        }
    }

    // 返回手势 / 返回键：逐级返回，不要一按就退 App
    BackHandler {
        when (vm.screen) {
            Screen.SPLASH, Screen.LOGIN, Screen.HOME -> (ctx as? android.app.Activity)?.finish()
            Screen.SCAN, Screen.PAY, Screen.HISTORY -> vm.backHome()
            Screen.DONE -> vm.backFromDone()
        }
    }

    when (vm.screen) {
        Screen.SPLASH -> SplashScreen()

        Screen.LOGIN -> LoginScreen(vm = vm)

        Screen.HOME -> HomeScreen(
            vm = vm,
            onScan = { vm.openScan() },
            onManual = { manualInput = true },
            onHistory = { vm.openHistory() },
            onRunning = { vm.openRunning() },
        )

        Screen.SCAN -> ScanScreen(
            onBack = { vm.backHome() },
            onManual = { manualInput = true },
            onCode = { code -> vm.onScanned(code) },
            onError = { msg -> vm.toast = msg },
        )

        Screen.PAY -> PayScreen(
            vm = vm,
            onBack = { vm.backHome() },
            onPay = { (ctx as? android.app.Activity)?.let { vm.pay(it) } },
        )

        Screen.DONE -> DoneScreen(vm = vm, onClose = { vm.stop() })

        Screen.HISTORY -> HistoryScreen(vm = vm, onBack = { vm.backHome() })
    }

    if (vm.busy) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Ink.mint)
        }
    }

    vm.loginExpired?.let { path ->
        AlertDialog(
            onDismissRequest = { vm.dismissExpired() },
            title = { Text("登录已过期") },
            text = {
                Text("接口 $path 返回「未登录」。\n服务端的 token 大约 1 小时就会失效，需要重新收验证码登录。")
            },
            confirmButton = {
                TextButton(onClick = { vm.relogin() }) { Text("重新登录") }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissExpired() }) { Text("稍后") }
            },
        )
    }

    if (manualInput) {
        AlertDialog(
            onDismissRequest = { manualInput = false },
            title = { Text("输入设备号") },
            text = {
                OutlinedTextField(
                    value = manualText,
                    onValueChange = { manualText = it },
                    singleLine = true,
                    label = { Text("设备号 / 二维码里的 NQT·IMEI·SN") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    manualInput = false
                    val v = manualText.trim()
                    if (v.isNotEmpty()) vm.onScanned(v)
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { manualInput = false }) { Text("取消") }
            },
        )
    }
}

/** 冷启动闪屏：等 DataStore 读 token 的那一两百毫秒，不要先闪一下登录页。 */
@Composable
private fun SplashScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            WindGlyph(width = 46.dp, height = 38.dp, color = Ink.mint2)
            Spacer(Modifier.height(16.dp))
            Text("轻风", color = Ink.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 6.sp)
            Spacer(Modifier.height(8.dp))
            Text("BREEZE · 一键吹风", color = Ink.dim2, fontSize = 10.sp, letterSpacing = 2.sp)
        }
    }
}
