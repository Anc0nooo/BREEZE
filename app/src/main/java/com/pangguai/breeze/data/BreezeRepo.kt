package com.pangguai.breeze.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ScanResult(
    val device: Device,
    val plans: List<Plan>,
    val channels: List<PayChannel>,
    val ticket: String? = null,
)

/**
 * 服务端链路（全部 form POST + header 签名，实测于 2026-10-29）。
 *
 * 登录：common/sms/sendCode {phone, template:"reg"} → user/reg {channel:"android_app", phone, verify}
 * 只读：goods/scan/v2 {NQT|IMEI|SN} → goods/normal/details {goodsId}
 *       → goods/normal/skus {goodsId} → payChannelRoute/prePay {...}（shopId 要传 orgId）
 *
 * 还没接的写链路（要真花钱，先留 TODO）：
 *   trade/create（额外 header：imei/categoryCode/lat/lng + orderRiskSign）
 *   → pay/unify/pay {orderNo, payType:"2"} → order/pay_status → trade/order/start
 */
class BreezeRepo(private val api: Api, private val session: Session? = null) {

    /** 是否已经拿到 token（有 session 时会从 DataStore 恢复，见 [restoreSession]）。 */
    val loggedIn: Boolean get() = api.token.isNotBlank()

    val phone: String get() = api.phone

    /** 上次登录用的原始手机号（给「重新登录」预填用）。 */
    val lastPhone: String get() = api.phone

    /**
     * 用当前 token 探一下服务端还认不认。
     *
     * 服务端的 token 很短命（实测一小时左右就失效），而且**只对新 token 生效**：
     * 拿旧 token 调 `goods/normal/skus`、`trade` 系列接口都会回 `code=2 未登录`。
     * 所以登录成功后立刻验一次，别等用户扫完码才告诉他「登录已过期」。
     */
    suspend fun verifySession(): Boolean = withContext(Dispatchers.IO) {
        if (api.token.isBlank()) return@withContext false
        runCatching {
            val body = api.post("user/info", emptyList())
            Api.isOk(body)
        }.getOrDefault(false)
    }

    /** 冷启动时把上次的 token/手机号读回 [Api]；返回是否处于已登录状态。 */
    suspend fun restoreSession(): Boolean = withContext(Dispatchers.IO) {
        val s = session ?: return@withContext api.token.isNotBlank()
        val (t, p) = s.load()
        // 竞态保护：读盘期间用户可能已经登录完（api.token 已被新 token 覆盖），
        // 这时**绝不能**把盘里的旧 token 写回去，否则会「刚登录完，一扫就说登录过期」。
        if (api.token.isNotBlank()) return@withContext true
        if (t.isNotBlank()) {
            api.token = t
            api.phone = p
        }
        t.isNotBlank()
    }

    private suspend fun persist() {
        session?.save(api.token, api.phone)
    }

    /** 退出登录：清掉内存和本地 token。 */
    suspend fun logout() = withContext(Dispatchers.IO) {
        api.token = ""
        api.phone = ""
        session?.clear()
    }

    /** 发短信验证码。成功时服务端 code=0，同一号码 60 秒内重发会回 2002。 */
    suspend fun sendCode(phone: String): String? = withContext(Dispatchers.IO) {
        val body = api.post(
            "common/sms/sendCode",
            listOf("phone" to phone.trim(), "template" to "reg"),
        )
        if (Api.isOk(body)) null else errorText(body, "验证码发送失败")
    }

    /** 验证码登录，成功后把 token 写进 [Api]，并立刻验一次 token 是否真的可用。 */
    suspend fun login(phone: String, code: String): String? = withContext(Dispatchers.IO) {
        val body = api.post(
            "user/reg",
            listOf(
                "channel" to Sign.CHANNEL,
                "phone" to phone.trim(),
                "verify" to code.trim(),
            ),
        )
        val data = body.optJSONObject("data")
        val token = data?.optString("token").orEmpty()
        if (!Api.isOk(body)) return@withContext errorText(body, "登录失败")
        if (data?.optBoolean("forcedBind") == true || !data.isNull("forcedBind") && token.isBlank()) {
            // 服务端回 code=0 但 token 为 null：这个号还绑着第三方账号（微信/支付宝），
            // 必须先走 BindExternalAccountAct（wechat/unionId/bind 或 alipay/openId/bind）。
            return@withContext "这个手机号需要先在官方 App 里绑定微信/支付宝，暂不支持"
        }
        if (token.isBlank()) return@withContext "登录没拿到 token（服务端未返回），换一个号试试"
        api.token = token
        api.phone = phone.trim()
        persist()
        if (!verifySession()) {
            api.token = ""
            api.phone = ""
            persist()
            return@withContext "登录成功但服务端不认这个 token，请重新获取验证码"
        }
        null
    }

    suspend fun loadByCode(raw: String): ScanResult = withContext(Dispatchers.IO) {
        val goodsId = scanGoodsId(raw)
        check(goodsId.isNotBlank()) { "二维码里没有 NQT / IMEI / SN" }

        val detail = api.post("goods/normal/details", listOf("goodsId" to goodsId))
        val d = detail.optJSONObject("data") ?: JSONObject()

        val skusBody = api.post(
            "goods/normal/skus",
            listOf("goodsId" to goodsId, "appointPage" to "false"),
        )
        val plans = parsePlans(pickArray(skusBody))

        val errCode = d.optString("deviceErrorCode")
        val online = errCode.isBlank() || errCode == "0" || errCode == "null"
        val device = Device(
            id = goodsId,
            name = d.optString("name").ifBlank { "吹风机" },
            area = d.optString("orgName").ifBlank { d.optString("categoryName") },
            status = if (online) "设备在线 · 空闲"
            else d.optString("deviceErrorMsg").ifBlank { "设备状态异常" },
            online = online,
            imei = d.optString("imei"),
            shopId = d.optString("orgId").ifBlank { d.optString("shopId") },
            categoryCode = d.optString("categoryCode").ifBlank { "03" },
        )

        val channels = runCatching {
            val imei = device.imei
            val shopId = device.shopId
            val price = plans.firstOrNull()?.let { it.priceFen } ?: 0
            if (imei.isBlank() || shopId.isBlank()) return@runCatching emptyList<PayChannel>()
            val body = api.post(
                "payChannelRoute/prePay",
                listOf(
                    "imei" to imei,
                    "goodsId" to goodsId,
                    // hitAd / useTicket 服务端按 Integer 绑定，combineOrderStatus 按 Boolean
                    "hitAd" to "0",
                    "payPrice" to fenToYuan(price),
                    "useTicket" to "0",
                    "shopId" to shopId,
                    "combineOrderStatus" to "false",
                    "confirmSelectedStatus" to "false",
                ),
            )
            parseChannels(pickArray(body))
        }.getOrDefault(emptyList())

        ScanResult(
            device = device,
            plans = plans.ifEmpty { demoPlans() },
            channels = channels.ifEmpty { demoChannels() },
        )
    }

    // ---- 写链路（真花钱，务必先看 [PayStep] 的顺序）----

    /**
     * 预结算：把「买哪一档」换成服务端认的 items JSON，顺便拿到它算的 realPrice。
     * 返回 (itemsJson, promotionsJson, realPriceFen)。
     */
    private fun preview(device: Device, plan: Plan): Triple<String, String, Int> {
        val items = itemsJson(device.id, plan)
        val body = api.post(
            "trade/preview/v4",
            listOf(
                "promotions" to "[]",
                "items" to items,
                "isAutoSelectPromotion" to "1",
            ),
        )
        val d = body.optJSONObject("data") ?: return Triple(items, "[]", plan.priceFen)
        val real = yuanToFen(d.optString("realPrice"))
        // **不要把 preview 返回的 promotions 原样回传**：本机实测它给的是
        // [{"originPrice":null,...,"promotionType":7,"isAvailable":true,...}]，
        // 原样传给 trade/create 会 code=-1（msg 为 null）。我们也不做券的交互，固定传空数组。
        return Triple(items, "[]", if (real > 0) real else plan.priceFen)
    }

    /**
     * 下单用的 items JSON。
     *
     * 吹风机是按「时长档」卖的，原 App（DryerSkuNewAct.toOrder）就是这么拼的：
     * `OrderPreviewQuest(goodsId, skuId, amount=time, soldType="1"(默认), num=time, price=""(默认))`
     * —— **amount 和 num 都填分钟数，price 留空让服务端按 perPrice 算**。
     * 之前传 amount=1、price=sku 的 discountPrice(0.09)，所以实付被算成 0.09。
     */
    private fun itemsJson(goodsId: String, plan: Plan): String {
        val gid = goodsId.filter { it.isDigit() }
        val sid = plan.skuId.filter { it.isDigit() }
        val n = if (plan.tier && plan.minutes > 0) plan.minutes else 1
        // 实测（2026-10-29 用活 token 跑 preview/v4 的 8 组矩阵）：
        //  - soldType 必须显式带 "1"，少了就 code=-1（msg 为 null）；
        //  - price 传空串 "" 也 code=-1，**干脆不传**最安全（服务端按 perPrice 算 0.50）；
        //  - goodsId/skuId 必须是字符串，传数字也 code=-1。
        return """[{"goodsId":"$gid","skuId":"$sid","amount":"$n","soldType":"1","num":"$n"}]"""
    }

    /** 只问价：给界面显示「实付 ¥x.xx」用，不改任何状态。 */
    suspend fun previewPrice(device: Device, plan: Plan): Int = withContext(Dispatchers.IO) {
        if (device.id.isBlank() || plan.skuId.isBlank()) return@withContext 0
        runCatching { preview(device, plan).third }.getOrDefault(0)
    }

    /**
     * 建单。返回 (orderNo, realPriceFen)。
     * 注意这条要带风控头：imei / categoryCode / lat / lng + orderRiskSign + orderRiskTimestamp，
     * 少了服务端会拒；lat/lng 没有定位就传 "0"，但两处必须一致。
     */
    suspend fun createOrder(device: Device, plan: Plan): Pair<String, Int> = withContext(Dispatchers.IO) {
        check(device.id.any { it.isDigit() }) { "设备号无效，重新扫码" }
        check(plan.skuId.any { it.isDigit() }) { "档位缺少 skuId，重新扫码" }
        check(device.imei.isNotBlank()) { "缺少 imei，重新扫码" }
        val (items, promotions, realFen) = preview(device, plan)
        val ts = System.currentTimeMillis().toString()
        val risk = Sign.orderRiskSign(
            path = "/trade/create",
            timestamp = ts,
            token = api.token,
            imei = device.imei,
            categoryCode = device.categoryCode,
            lat = "0",
            lng = "0",
        )
        val body = api.post(
            "trade/create",
            listOf("promotions" to promotions, "items" to items),
            mapOf(
                "imei" to device.imei,
                "categoryCode" to device.categoryCode,
                "lat" to "0",
                "lng" to "0",
                "orderRiskSign" to risk,
                "orderRiskTimestamp" to ts,
            ),
        )
        val d = body.optJSONObject("data")
        val tradeNo = d?.optString("tradeNo").orEmpty()
        check(tradeNo.isNotBlank()) { errorText(body, "建单失败") }
        tradeNo to realFen
    }

    /**
     * 取支付参数（pay/unify/prePay 的 prepayParam）。
     * 支付宝：一串 orderInfo（直接丢给 PayTask.payV2）；
     * 微信：一段 JSON（appid/partnerid/prepayid/noncestr/timestamp/sign/package）。
     */
    suspend fun prepayParam(orderNo: String, payType: Int): String = withContext(Dispatchers.IO) {
        val body = api.post(
            "pay/unify/prePay",
            listOf(
                "orderNo" to orderNo,
                "payType" to payType.toString(),
                "againFlag" to "1",
            ),
        )
        val info = clean(body.optJSONObject("data")?.optString("prepayParam"))
        check(info.isNotBlank()) { errorText(body, "获取支付参数失败") }
        info
    }

    suspend fun alipayOrderInfo(orderNo: String, payType: Int): String = prepayParam(orderNo, payType)

    suspend fun wechatOrderInfo(orderNo: String, payType: Int): String = prepayParam(orderNo, payType)

    /** 服务端确认是否已付（order/pay_status，返回 true/false）。 */
    suspend fun payStatus(orderNo: String, payType: Int): Boolean = withContext(Dispatchers.IO) {
        val body = api.post(
            "order/pay_status",
            listOf("orderNo" to orderNo, "payType" to payType.toString()),
        )
        Api.isOk(body) && body.optBoolean("data", false)
    }

    /** 支付成功后启动设备（trade/order/start）。 */
    suspend fun startDevice(orderNo: String): Boolean = withContext(Dispatchers.IO) {
        val body = api.post("trade/order/start", listOf("orderNo" to orderNo))
        Api.isOk(body)
    }

    /** 设备是否真的启动起来了（machine/device_is_start）。 */
    suspend fun deviceIsStart(orderNo: String): Boolean = withContext(Dispatchers.IO) {
        val body = api.post("machine/device_is_start", listOf("orderNo" to orderNo))
        Api.isOk(body) && body.optBoolean("data", false)
    }

    private fun scanGoodsId(raw: String): String {
        val text = raw.trim()
        val uri = Uri.parse(text)
        val names = runCatching { uri.queryParameterNames.toList() }.getOrDefault(emptyList())
        for (key in listOf("NQT", "IMEI", "SN")) {
            if (names.contains(key)) {
                val v = uri.getQueryParameter(key)
                if (!v.isNullOrBlank()) {
                    val body = api.post("goods/scan/v2", listOf(key to v))
                    return body.optJSONObject("data")?.optString("id").orEmpty()
                }
            }
        }
        // 不是标准二维码：直接把整串当 SN 试一次
        val body = api.post("goods/scan/v2", listOf("SN" to text))
        return body.optJSONObject("data")?.optString("id").orEmpty()
    }

    private fun pickArray(body: JSONObject): JSONArray {
        body.optJSONArray("data")?.let { return it }
        body.optJSONObject("data")?.optJSONArray("data")?.let { return it }
        return JSONArray()
    }

    /**
     * 一个 sku 里可能挂着多档 `extAttr.perPrice[]`（吹风机就是这种：5 分钟 / 6 分钟…），
     * 每档的 price 是展示价；真正下单时服务端按 sku 的 discountPrice 算。
     *
     * perPrice 里会有大量重复档（实测 5 分钟出现 4 次、只有一条 status=true），
     * 所以按分钟去重，优先保留被标记为默认的那条。
     */
    private fun parsePlans(arr: JSONArray): List<Plan> {
        val raw = ArrayList<Plan>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val skuId = o.optString("skuId")
            val skuPrice = yuanToFen(o.optString("discountPrice").ifBlank { o.optString("price") })

            val tiers = runCatching {
                JSONObject(o.optString("extAttr").ifBlank { "{}" }).optJSONArray("perPrice")
            }.getOrNull()

            if (tiers != null && tiers.length() > 0) {
                // perPrice 里同一个时长会重复出现，用 status 标记哪一条才是这台机器当前卖的档：
                // 本机实测 = [{5,0.5,status:false}×4, {6,0.5,status:true}]，只有 6 分钟那条能选。
                val enabled = ArrayList<JSONObject>()
                for (j in 0 until tiers.length()) {
                    val t = tiers.optJSONObject(j) ?: continue
                    if (t.optBoolean("status", false)) enabled += t
                }
                val use = if (enabled.isNotEmpty()) enabled else {
                    val all = ArrayList<JSONObject>()
                    for (j in 0 until tiers.length()) tiers.optJSONObject(j)?.let { all += it }
                    all
                }
                for (t in use) {
                    val minutes = t.optInt("time", 0)
                    val fen = yuanToFen(t.opt("price")?.toString() ?: "")
                    if (minutes <= 0 || fen <= 0) continue
                    raw += Plan(
                        minutes = minutes,
                        priceFen = fen,
                        badge = if (t.optBoolean("status", false)) "默认档" else null,
                        skuId = skuId,
                        // 吹风机按时长档下单：items 里 amount/num 都填分钟数，价格让服务端算
                        tier = true,
                    )
                }
                continue
            }

            if (skuPrice <= 0) continue
            val minutes = o.optInt("amount", 0).takeIf { it > 0 }
                ?: clean(o.optString("name")).filter { it.isDigit() }.toIntOrNull()
                ?: 0
            raw += Plan(
                minutes = if (minutes > 0) minutes else 1,
                priceFen = skuPrice,
                badge = if (o.optBoolean("defaultModel", false)) "默认档" else null,
                skuId = skuId,
                tier = false,
            )
        }
        // 同一档位只留一条：优先带「默认档」的那条
        val dedup = LinkedHashMap<Int, Plan>()
        for (p in raw) {
            val old = dedup[p.minutes]
            if (old == null || (old.badge == null && p.badge != null)) dedup[p.minutes] = p
        }
        return dedup.values.sortedBy { it.minutes }
    }

    private fun parseChannels(arr: JSONArray): List<PayChannel> {
        val out = ArrayList<PayChannel>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val method = o.optInt("method", -1)
            if (method < 0) continue
            val canChecked = !o.has("canChecked") || o.isNull("canChecked") || o.optBoolean("canChecked")
            // 服务端这些字段常常是 JSON null，optString 会给出字符串 "null"，先洗一遍
            val remark = clean(o.optString("remark")).ifBlank { clean(o.optString("countInfo")) }
            // 只接了支付宝(13) 和微信(32)；银联/银行 App 需要各自 SDK，标不可选免得点了才发现调不起来
            // 只有支付宝(13) 能真正走通：服务端返回的 prepayParam 是签好名的 orderStr，
            // 交给支付宝 App 支付即可（app_id 属于商户，支付宝只校验服务端签名，不校验调用方签名）。
            // 微信(32) 的 prepayParam 里 appid=wxfe32bf17aed1de7a 是商户的，微信要求「该 appid 绑定的
            // 包名+签名」才能拉起支付，我们用自己的签名注册不了它，只能标成暂未接入。
            val supported = method == 13
            out += PayChannel(
                code = method,
                name = clean(o.optString("methodName")).ifBlank { "渠道 $method" },
                sub = remark,
                tag = when {
                    !canChecked -> "不可用"
                    !supported -> "暂未接入"
                    else -> clean(o.optString("thirdChannel")).ifBlank { null }
                },
                recommended = o.optBoolean("defaultFlag", false),
                enabled = canChecked && supported,
            )
        }
        return out
    }

    /**
     * 历史订单。接口 order/list（ApiService.java:796），body 只有 page/pageSize。
     * 实测返回 `data = {total, items:[...]}` —— 列表键是 items，不是 list。
     */
    suspend fun orders(page: Int = 1, size: Int = 20): List<OrderRecord> = withContext(Dispatchers.IO) {
        val body = api.post("order/list", listOf("page" to "$page", "pageSize" to "$size"))
        if (!Api.isOk(body)) error(errorText(body, "获取订单失败"))
        val data = body.optJSONObject("data") ?: return@withContext emptyList()
        val arr = data.optJSONArray("items") ?: data.optJSONArray("list") ?: JSONArray()
        val out = ArrayList<OrderRecord>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val price = clean(o.optString("payPrice")).ifBlank { clean(o.optString("markPrice")) }
            out += OrderRecord(
                orderNo = clean(o.optString("orderNo")).ifBlank { clean(o.optString("id")) },
                machineName = clean(o.optString("machineName")).ifBlank { "吹风机" },
                shopName = clean(o.optString("shopName")),
                createTime = clean(o.optString("createTime")),
                priceFen = yuanToFen(price),
                minutes = o.optInt("markMinutes", 0),
                status = o.optInt("orderStatus", -1),
            )
        }
        out
    }

    /** 吹风会话：存「结束时刻」，进程被杀、切后台都不影响倒计时。 */
    suspend fun saveRun(r: StoredRun) {
        session?.saveRun(r)
    }

    suspend fun loadRun(): StoredRun? = session?.loadRun()

    suspend fun clearRun() {
        session?.clearRun()
    }

    private fun errorText(body: JSONObject, fallback: String): String {
        val code = body.optInt("code", Int.MIN_VALUE)
        val http = body.optInt("_http", 0)
        // 网关回 HTML（典型是 405）：不是业务错误，别把网页源码糊到界面上
        if (body.optBoolean("_html") || (http in 400..599 && code == Int.MIN_VALUE)) {
            return when (http) {
                405, 403 -> "服务端暂时拒绝了请求（HTTP $http），可能被风控拦了，一会儿再试"
                in 500..599 -> "服务端出错了（HTTP $http），稍后再试"
                else -> "请求失败（HTTP $http），稍后再试"
            }
        }
        val msg = clean(body.optString("msg")).ifBlank { clean(body.optString("message")) }
        val raw = clean(body.optString("_raw")).take(120)
        val head = when {
            msg.isNotBlank() -> msg
            raw.isNotBlank() -> raw
            code != Int.MIN_VALUE -> "code=$code"
            else -> fallback
        }
        return if (code != Int.MIN_VALUE && code != 0) "$head（$code）" else head
    }

    companion object {
        /** 服务端把 null 塞进字符串字段时，optString 会返回字面量 "null"。 */
        fun clean(s: String?): String {
            val t = s?.trim().orEmpty()
            return if (t.equals("null", true) || t.equals("undefined", true)) "" else t
        }
        fun demoPlans() = listOf(
            Plan(15, 150, skuId = "0"),
            Plan(30, 250, badge = "默认档", skuId = "0"),
            Plan(60, 450, skuId = "0"),
        )

        fun demoChannels() = listOf(
            PayChannel(52, "银联支付", "云闪付 / 银行卡", recommended = true),
            PayChannel(13, "支付宝", "跳转支付宝付款"),
            PayChannel(32, "微信支付", "跳转微信付款"),
        )

        fun yuanToFen(text: String): Int {
            val t = text.trim().removePrefix("¥").removePrefix("￥")
            if (t.isEmpty() || t == "null") return 0
            val parts = t.split(".")
            val yuan = parts[0].filter { it.isDigit() }.toIntOrNull() ?: 0
            val cent = if (parts.size > 1) {
                parts[1].filter { it.isDigit() }.padEnd(2, '0').take(2).toIntOrNull() ?: 0
            } else 0
            return yuan * 100 + cent
        }

        fun fenToYuan(fen: Int): String = formatFen(fen)
    }
}
