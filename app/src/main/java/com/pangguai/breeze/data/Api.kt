package com.pangguai.breeze.data

import android.net.Uri
import android.os.Build
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 服务端接口。全部是 form POST（application/x-www-form-urlencoded），
 * header 签名见 [Sign]，body 里额外追加 token（App 的 HeadInterceptor 就是这么干的）。
 */
class Api(var baseUrl: String = DEFAULT_BASE) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /** 登录后写入；为空表示未登录。 */
    var token: String = ""

    /** 登录用的手机号，只用于界面显示。 */
    var phone: String = ""

    /** 设备标识（App 发 oaid，这里留空也能跑，个别风控接口可能需要）。 */
    var deviceId: String = ""

    fun post(
        path: String,
        params: List<Pair<String, String>>,
        extraHeaders: Map<String, String> = emptyMap(),
    ): JSONObject {
        val clean = params.filter { it.second.isNotEmpty() }
        val ts = System.currentTimeMillis().toString()

        val body = StringBuilder()
        clean.forEach { (k, v) ->
            if (body.isNotEmpty()) body.append('&')
            body.append(Uri.encode(k)).append('=').append(Uri.encode(v))
        }
        if (token.isNotEmpty()) {
            if (body.isNotEmpty()) body.append('&')
            body.append("token=").append(Uri.encode(token))
        }

        val builder = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/" + path.trimStart('/'))
            .header("Authorization", token)
            .header("version", Sign.VERSION)
            .header("channel", Sign.CHANNEL)
            .header("phoneBrand", Build.BRAND ?: "Android")
            .header("deviceId", deviceId)
            // 实测：header 名是 Timestamp / Sign（大小写不敏感，但小写 combo 会被服务端
            // 当成「没带签名的老客户端」，回一句误导性的「版本过低」）。
            .header("Timestamp", ts)
            .header("Sign", Sign.headerSign("/" + path.trimStart('/'), ts, token))
            .header("User-Agent", USER_AGENT)

        extraHeaders.forEach { (k, v) -> if (v.isNotEmpty()) builder.header(k, v) }

        val req = builder.post(body.toString().toRequestBody(FORM)).build()

        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (text.isBlank()) return JSONObject()
            val json = runCatching { JSONObject(text) }.getOrElse {
                JSONObject().put("_raw", text.take(2000)).put("_http", resp.code)
            }
            if (!resp.isSuccessful) json.put("_http", resp.code)
            // 网关（阿里云 405 页之类）回的是 HTML，不是业务 JSON。标出来，
            // 界面别把一坨 <!doctype html> 甩给用户。
            if (text.trimStart().startsWith("<")) json.put("_html", true)
            // code=2 就是「未登录」：token 过期（实测约 1 小时）或被别的设备顶掉。
            // 带上 path 抛出去，界面上能直接看到是哪个接口回的未登录。
            if (token.isNotEmpty() && json.optString("code") == "2") throw SessionExpired(path)
            return json
        }
    }

    companion object {
        const val DEFAULT_BASE = "https://userapi.qiekj.com/"
        private val FORM = "application/x-www-form-urlencoded;charset=UTF-8".toMediaType()
        private val USER_AGENT =
            "QEUser/${Sign.VERSION} (com.qiekj.user; build:1440; Android 13; " +
                "userChannel:android_app; version:${Sign.VERSION}) OkHttp/4.9.3"

        fun isOk(json: JSONObject): Boolean {
            val code = json.optString("code", "")
            return code == "0" || code == "200" || json.optBoolean("success", false)
        }
    }
}

/** token 过期 / 被顶掉：服务端回 code=2。[path] 是回这个码的接口，便于定位。 */
class SessionExpired(val path: String) : Exception("登录已过期（$path）")
