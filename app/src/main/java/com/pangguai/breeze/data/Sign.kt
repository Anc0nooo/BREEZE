package com.pangguai.breeze.data

import java.security.MessageDigest

/**
 * 逆向自 com.qiekj.user 1.144.0 的签名规则。
 *
 * 实测结论（反编译源码 + 调用点核对）：
 *   App 里 `SignInterceptor`（body 的 _timestamp/_sign/_appid）**从未被注册**，是死代码；
 *   线上只签 header，签法见 [headerSign]。
 */
object Sign {
    const val CHANNEL = "android_app"
    const val VERSION = "1.144.0"
    private const val APP_SECRET = "nFU9pbG8YQoAe1kFh+E7eyrdlSLglwEJeA0wwHB1j5o="

    fun hex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xff
            if (v < 16) sb.append('0')
            sb.append(Integer.toHexString(v))
        }
        return sb.toString()
    }

    fun sha256(text: String): String =
        hex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)))

    /**
     * HeadInterceptor：
     * SHA-256("appSecret=<secret>&channel=android_app&timestamp=<ts>&token=<token>&version=<ver>&<encodedPath>")
     *
     * @param path 必须是带前导斜杠的 encodedPath，例如 "/goods/scan/v2"
     */
    fun headerSign(path: String, timestamp: String, token: String): String {
        val raw = "appSecret=$APP_SECRET&channel=$CHANNEL&timestamp=$timestamp" +
            "&token=$token&version=$VERSION&$path"
        return sha256(raw)
    }

    /**
     * HeadInterceptor 对 /trade/create、/goods/shower/unlock、/goods/water/unlock 额外加的一层风控签名，
     * header 名是 orderRiskSign + orderRiskTimestamp。注意它的 secret 与 headerSign 是同一串（App 里两个常量同值）。
     * 拼接顺序固定：appSecret, categoryCode, channel, imei, lat, lng, timestamp, token, version, path。
     */
    fun orderRiskSign(
        path: String,
        timestamp: String,
        token: String,
        imei: String,
        categoryCode: String,
        lat: String,
        lng: String,
    ): String {
        val raw = "appSecret=$APP_SECRET&categoryCode=$categoryCode&channel=$CHANNEL" +
            "&imei=$imei&lat=$lat&lng=$lng&timestamp=$timestamp&token=$token&version=$VERSION&$path"
        return sha256(raw)
    }
}
