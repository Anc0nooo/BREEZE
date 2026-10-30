package com.pangguai.breeze.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import com.pangguai.breeze.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 一条更新记录，对应服务器上的 latest.json：
 *
 * ```json
 * {
 *   "versionCode": 3,
 *   "versionName": "1.2.0",
 *   "url": "breeze-1.2.0.apk",
 *   "notes": "修了什么什么",
 *   "size": 13194763,
 *   "force": false
 * }
 * ```
 *
 * url 写相对路径即可，相对 [BuildConfig.UPDATE_BASE]。
 */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val url: String,
    val notes: String,
    val force: Boolean,
    val size: Long,
)

/** 更新流程里能解释给人听的那种错误（404、返回网页而不是 JSON 等）。 */
class UpdateException(message: String) : Exception(message)

/**
 * 自己给自己发版：查 latest.json → 下 APK → PackageInstaller 静默装。
 *
 * 关键前提：**所有人装过的包必须是同一个签名**（keystore.properties 里那把）。
 * 签名一换，系统只会报「应用未安装 / 签名不一致」，只能卸载重装。
 */
object Updater {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun base(): String = BuildConfig.UPDATE_BASE.trim().trimEnd('/') + "/"

    /** 有新版就返回它，没有 / 网络失败就返回 null（更新检查失败绝不能挡人用 App）。 */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(base() + "latest.json?t=" + System.currentTimeMillis())
                .header("Cache-Control", "no-cache")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw UpdateException("更新地址返回 HTTP ${resp.code}（${base()}latest.json）")
                }
                val text = resp.body?.string().orEmpty()
                if (text.isBlank()) throw UpdateException("更新地址返回了空内容")
                // SPA 站点常见的坑：目录下没有这个文件时，nginx 会回 index.html（200 + HTML），
                // 这里必须认出来，否则永远「检查不到更新」而且不知道为什么。
                if (text.trimStart().startsWith("<")) {
                    throw UpdateException("更新地址返回的是网页而不是 JSON，latest.json 没传上去？")
                }
                val o = JSONObject(text)
                val code = o.optInt("versionCode", 0)
                if (code <= BuildConfig.VERSION_CODE) return@withContext null
                val url = o.optString("url").ifBlank { "breeze-$code.apk" }
                UpdateInfo(
                    versionCode = code,
                    versionName = o.optString("versionName").ifBlank { code.toString() },
                    url = if (url.startsWith("http")) url else base() + url.trimStart('/'),
                    notes = o.optString("notes"),
                    force = o.optBoolean("force", false),
                    size = o.optLong("size", 0L),
                )
            }
        }.getOrNull()
    }

    /** 手动检查时用：把失败原因原样抛出来，别静默。 */
    suspend fun checkOrThrow(): UpdateInfo? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(base() + "latest.json?t=" + System.currentTimeMillis())
            .header("Cache-Control", "no-cache")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw UpdateException("更新地址返回 HTTP ${resp.code}")
            val text = resp.body?.string().orEmpty()
            if (text.isBlank()) throw UpdateException("更新地址返回了空内容")
            if (text.trimStart().startsWith("<")) {
                throw UpdateException("更新地址返回的是网页而不是 JSON，latest.json 没传上去？")
            }
            val o = JSONObject(text)
            val code = o.optInt("versionCode", 0)
            if (code <= BuildConfig.VERSION_CODE) return@withContext null
            val url = o.optString("url").ifBlank { "breeze-$code.apk" }
            UpdateInfo(
                versionCode = code,
                versionName = o.optString("versionName").ifBlank { code.toString() },
                url = if (url.startsWith("http")) url else base() + url.trimStart('/'),
                notes = o.optString("notes"),
                force = o.optBoolean("force", false),
                size = o.optLong("size", 0L),
            )
        }
    }

    /** 下载到 cacheDir，回调进度 0f..1f。 */
    suspend fun download(ctx: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val out = File(ctx.cacheDir, "breeze-${info.versionCode}.apk")
            if (out.exists()) out.delete()
            client.newCall(Request.Builder().url(info.url).build()).execute().use { resp ->
                if (!resp.isSuccessful) error("下载失败（HTTP ${resp.code}）")
                val body = resp.body ?: error("下载失败：响应为空")
                val total = if (info.size > 0) info.size else body.contentLength()
                body.byteStream().use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read: Long = 0
                        var last = 0f
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            output.write(buf, 0, n)
                            read += n
                            if (total > 0) {
                                val p = (read.toFloat() / total).coerceIn(0f, 1f)
                                if (p - last >= 0.01f) {
                                    last = p
                                    onProgress(p)
                                }
                            }
                        }
                    }
                }
            }
            if (out.length() < 1024) error("下载的文件不完整，重试一次")
            out
        }

    /**
     * 用 PackageInstaller 会话装自己：不用 FileProvider、不用外部存储权限，
     * 系统会弹「是否安装」的确认框（第一次还要允许「安装未知应用」）。
     */
    fun install(ctx: Context, file: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("breeze.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out, 64 * 1024) }
                session.fsync(out)
            }
            val intent = Intent(ctx, UpdateInstallReceiver::class.java)
                .putExtra(EXTRA_SESSION, sessionId)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            val pending = PendingIntent.getBroadcast(ctx, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    const val EXTRA_SESSION = "session_id"

    /** 当前装的版本号，界面上显示用。 */
    fun currentVersion(): String = "${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）"
}

/** 安装结果回调：系统会先弹确认框，这一段就是把确认框拉起来。 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(
                    intent, Intent.EXTRA_INTENT, Intent::class.java,
                ) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> {
                // 失败一般是签名不一致，或者用户点了取消
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                android.util.Log.w("BreezeUpdate", "install failed status=$status msg=$msg")
            }
        }
    }
}
