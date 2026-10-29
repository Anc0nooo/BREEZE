package com.pangguai.breeze.data

enum class Screen { LOGIN, HOME, SCAN, PAY, DONE, HISTORY }

/** 设备（吹风机）。id 是扫码得到的设备号，名称/区域来自 goods/normal/details 的返回。 */
data class Device(
    val id: String,
    val name: String = "吹风机",
    val area: String = "",
    val status: String = "设备在线 · 空闲",
    val online: Boolean = true,
    /** 下单要用：details 返回的 imei / orgId（收银台的 shopId）/ categoryCode（吹风机=03）。 */
    val imei: String = "",
    val shopId: String = "",
    val categoryCode: String = "03",
)

/**
 * 时长档位。价格以「分」为单位存，避免浮点误差；UI 再格式化成 ¥x.xx。
 *
 * 实测一个 sku 可以带多档：`extAttr.perPrice[] = {price, time, status}`，
 * 此时 [priceFen] 是展示价、[payPriceFen] 是真正下单的 sku 价（服务端按 sku 算钱）。
 */
data class Plan(
    val minutes: Int,
    val priceFen: Int,
    val badge: String? = null,
    val skuId: String = "",
    /** true = 来自 sku.extAttr.perPrice[] 的时长档（吹风机），下单时 amount/num 都填分钟数。 */
    val tier: Boolean = false,
) {
    val priceText: String get() = "¥" + formatFen(priceFen)
}

/** 收银渠道。code 对应服务端 payChannelRoute/prePay 返回的 payType/channelCode。 */
data class PayChannel(
    val code: Int,
    val name: String,
    val sub: String,
    val tag: String? = null,
    val recommended: Boolean = false,
    val enabled: Boolean = true,
)

enum class StepPhase { WAIT, DOING, DONE }

data class StepState(
    val title: String,
    val sub: String,
    val phase: StepPhase = StepPhase.WAIT,
)

fun formatFen(fen: Int): String {
    val yuan = fen / 100
    val cent = fen % 100
    return if (cent == 0) "$yuan.00" else "$yuan." + (if (cent < 10) "0$cent" else "$cent")
}

/**
 * 历史订单（order/list 的 data.items[]）。
 *
 * 文案映射按官方 App 的 OrderMyAdapter.java:70-95：
 * 0 未支付（超 5 分钟显示支付超时）、1 支付超时、2 已支付、3 已完成、4 退款中、5 已退款。
 */
data class OrderRecord(
    val orderNo: String,
    val machineName: String,
    val shopName: String,
    val createTime: String,
    val priceFen: Int,
    val minutes: Int,
    val status: Int,
) {
    val statusText: String
        get() = when (status) {
            0 -> "未支付"
            1 -> "支付超时"
            2 -> "已支付"
            3 -> "已完成"
            4 -> "退款中"
            5 -> "已退款"
            else -> "未知"
        }

    val isDone: Boolean get() = status == 2 || status == 3
    val priceText: String get() = "¥" + formatFen(priceFen)
    val shortNo: String get() = "NO." + orderNo.takeLast(8)
}
