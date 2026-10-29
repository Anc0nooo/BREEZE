package com.pangguai.breeze.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.pangguai.breeze.ui.theme.Ink

/**
 * 竖屏扫码：CameraX 预览 + ZXing 解码。
 *
 * 不用 zxing-android-embedded 自带的 CaptureActivity —— 那是独立 Activity，
 * 会锁横屏、自带一套取景 UI，和我们的深色竖屏对不上。这里自己绑定预览，
 * 只把 Y 平面（亮度）喂给 ZXing，QR 检测本身是旋转无关的。
 */
@Composable
fun CameraQrScanner(
    modifier: Modifier = Modifier,
    onCode: (String) -> Unit,
    onError: (String) -> Unit = {},
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var denied by remember { mutableStateOf(false) }

    val askPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok ->
        granted = ok
        denied = !ok
    }

    LaunchedEffect(Unit) {
        if (!granted) askPermission.launch(Manifest.permission.CAMERA)
    }

    if (!granted) {
        Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text(
                if (denied) "没有相机权限，去系统设置里打开\n或直接手动输入设备号" else "正在请求相机权限…",
                color = Ink.dim,
                fontSize = 13.sp,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }

    val previewView = remember {
        PreviewView(ctx).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val currentOnCode by rememberUpdatedState(onCode)
    var handled by remember { mutableStateOf(false) }
    // 解码放独立线程，别占主线程，否则预览会掉帧
    val analysisExecutor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }

    DisposableEffect(granted) {
        val executor = ContextCompat.getMainExecutor(ctx)
        val future = ProcessCameraProvider.getInstance(ctx)

        future.addListener({
            runCatching {
                val provider = future.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(analysisExecutor) { image ->
                    if (!handled) {
                        val text = decodeQr(image)
                        if (text != null) {
                            handled = true
                            currentOnCode(text)
                        }
                    }
                    image.close()
                }

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }.onFailure { onError("相机启动失败：" + (it.message ?: "未知错误")) }
        }, executor)

        onDispose {
            runCatching { future.get().unbindAll() }
            runCatching { analysisExecutor.shutdown() }
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier.fillMaxSize())
}

private val READER = MultiFormatReader().apply {
    setHints(
        mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(com.google.zxing.BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.CHARACTER_SET to "UTF-8",
        )
    )
}

/** 从 YUV_420_888 里取 Y 平面直接解码；QR 的检测本身与旋转无关，不需要转图。 */
private fun decodeQr(image: ImageProxy): String? {
    val plane = image.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val width = image.width
    val height = image.height
    val rowStride = plane.rowStride
    if (width <= 0 || height <= 0 || rowStride < width) return null

    val data = ByteArray(rowStride * height)
    runCatching {
        buffer.rewind()
        buffer.get(data, 0, minOf(data.size, buffer.remaining()))
    }.getOrElse { return null }

    return runCatching {
        val source = PlanarYUVLuminanceSource(
            data,
            rowStride,
            height,
            0,
            0,
            width.coerceAtMost(rowStride),
            height,
            false,
        )
        READER.decodeWithState(BinaryBitmap(HybridBinarizer(source)))?.text
    }.getOrNull().also { READER.reset() }
}
