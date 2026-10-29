package com.pangguai.breeze.data

import android.content.Context
import com.tencent.mm.opensdk.constants.Build
import com.tencent.mm.opensdk.modelpay.PayReq
import com.tencent.mm.opensdk.openapi.IWXAPI
import com.tencent.mm.opensdk.openapi.WXAPIFactory
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject

/**
 * 微信 App 支付。
 *
 * 流程和支付宝不同：`sendReq` 只是把请求丢给微信客户端，结果要等微信回调
 * `<包名>.wxapi.WXEntryActivity`，所以这里用一个单例把回调转成挂起点。
 */
object Wechat {

    /** WXEntryActivity 回来后把 errCode 交到这里。 */
    private var waiter: CompletableDeferred<Int>? = null

    fun fromCallback(errCode: Int) {
        waiter?.complete(errCode)
        waiter = null
    }

    fun newWaiter(): CompletableDeferred<Int> = CompletableDeferred<Int>().also { waiter = it }

    fun hint(errCode: Int?): String = when (errCode) {
        0 -> "支付成功"
        -1 -> "微信支付失败（多为 appId 未绑定本应用签名）"
        -2 -> "已取消支付"
        null -> "微信没有返回结果"
        else -> "微信支付失败（errCode=$errCode）"
    }

    private fun api(ctx: Context, appId: String): IWXAPI =
        WXAPIFactory.createWXAPI(ctx.applicationContext, appId, true)

    /** 返回 null 表示已经拉起微信，等 [newWaiter]；否则是失败原因。 */
    fun send(ctx: Context, json: String): String? {
        val obj = runCatching { JSONObject(json) }.getOrNull()
            ?: return "微信支付参数不是 JSON"
        val appId = pick(obj, "appid", "appId")
        if (appId.isBlank()) return "微信支付参数缺 appid"

        val wx = api(ctx, appId)
        if (!wx.isWXAppInstalled) return "没装微信"
        if (wx.wxAppSupportAPI < Build.PAY_SUPPORTED_SDK_INT) {
            return "微信版本太低，不支持支付"
        }
        if (!wx.registerApp(appId)) {
            // 走到这里基本就是「appId 与当前 APK 签名不匹配」
            return "微信 SDK 注册失败：appId 未绑定本应用签名"
        }

        val req = PayReq().apply {
            this.appId = appId
            partnerId = pick(obj, "partnerid", "partnerId", "mch_id")
            prepayId = pick(obj, "prepayid", "prepayId")
            nonceStr = pick(obj, "noncestr", "nonceStr", "nonce_str")
            timeStamp = pick(obj, "timestamp", "timeStamp")
            packageValue = pick(obj, "package", "packageValue").ifBlank { "Sign=WXPay" }
            sign = pick(obj, "sign")
        }
        if (req.partnerId.isBlank() || req.prepayId.isBlank() || req.sign.isBlank()) {
            return "微信支付参数不完整"
        }
        return if (wx.sendReq(req)) null else "拉起微信失败"
    }

    private fun pick(o: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = BreezeRepo.clean(o.optString(k))
            if (v.isNotBlank()) return v
        }
        return ""
    }
}
