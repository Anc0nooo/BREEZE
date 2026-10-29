package com.pangguai.breeze.data

import android.app.Activity
import com.alipay.sdk.app.PayTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 支付宝收银台。
 *
 * 服务端 pay/unify/prePay 返回的 prepayParam 就是支付宝「app 支付」的 orderInfo 串，
 * 原 App 也是把它交给 `PayTask.payV2(orderInfo, true)`（见 AliSdkExtKt.alipay），
 * 回调 resultStatus == "9000" 即付款成功。payV2 会阻塞，必须在 IO 线程跑。
 */
object Alipay {

    const val STATUS_SUCCESS = "9000"
    const val STATUS_CANCEL = "6001"

    /** 返回 resultStatus；常见值：9000 成功、6001 用户取消、4000 失败、8000 处理中。 */
    suspend fun pay(activity: Activity, orderInfo: String): String = withContext(Dispatchers.IO) {
        runCatching {
            PayTask(activity).payV2(orderInfo, true)["resultStatus"] ?: "unknown"
        }.getOrElse { "error:" + (it.message ?: "exception") }
    }

    fun hint(status: String): String = when {
        status == STATUS_SUCCESS -> "支付成功"
        status == STATUS_CANCEL -> "已取消支付"
        status == "8000" -> "支付处理中，稍后确认"
        status.startsWith("error:") -> "调不起支付宝：" + status.removePrefix("error:")
        else -> "支付未完成（$status）"
    }
}
