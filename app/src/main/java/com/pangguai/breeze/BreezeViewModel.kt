package com.pangguai.breeze

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pangguai.breeze.data.Alipay
import com.pangguai.breeze.data.BreezeRepo
import com.pangguai.breeze.data.Device
import com.pangguai.breeze.data.OrderRecord
import com.pangguai.breeze.data.PayChannel
import com.pangguai.breeze.data.Plan
import com.pangguai.breeze.data.Screen
import com.pangguai.breeze.data.SessionExpired
import com.pangguai.breeze.data.StepPhase
import com.pangguai.breeze.data.StepState
import com.pangguai.breeze.data.StoredRun
import com.pangguai.breeze.data.Wechat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.pangguai.breeze.data.formatFen

/**
 * 单屏状态机：HOME -> SCAN -> PAY -> DONE。
 *
 * 网络部分（扫码取设备、取档位、取渠道、建单、支付、启动）全部收敛在 [com.pangguai.breeze.data.BreezeRepo]，
 * 这里只负责界面状态；网络失败时回落到示例数据，保证界面可以先跑通。
 */
class BreezeViewModel(app: Application) : AndroidViewModel(app) {

    /**
     * 启动即 SPLASH：先闭嘴把 DataStore 里的 token 读回来、验一次，
     * 验过了直接进首页。**不要**先画登录页再跳走，那一帧闪屏很难看。
     */
    var screen by mutableStateOf(Screen.SPLASH)
        private set

    var phone by mutableStateOf("")
        private set

    /** 登录页输入框。 */
    var phoneInput by mutableStateOf("")
        private set
    var codeInput by mutableStateOf("")
        private set
    var codeSent by mutableStateOf(false)
        private set
    /** 短信重发倒计时（秒），0 表示可以点「获取验证码」。 */
    var codeCooldown by mutableStateOf(0)
        private set

    var ticket by mutableStateOf("12.80")

    var device by mutableStateOf(Device(id = "", name = "吹风机", area = "", status = "设备在线 · 空闲"))
        private set

    var plans by mutableStateOf(
        listOf(
            Plan(15, 150),
            Plan(30, 250, badge = "默认档"),
            Plan(60, 450),
        )
    )
        private set

    var planIdx by mutableStateOf(1)
    var channels by mutableStateOf(
        listOf(
            PayChannel(15, "支付宝免密", "签约后代扣，无需输密码", recommended = true),
            PayChannel(53, "云闪付无感", "银联代扣", tag = "备用"),
            PayChannel(2, "钱包（小票）", "仅小票抵扣，需商家开启", tag = "需商家开启", enabled = false),
        )
    )
        private set
    var channelIdx by mutableStateOf(0)

    var orderNo by mutableStateOf("")
        private set

    /** 服务端 preview/v4 给出的实付金额（分），0 表示还没问到、先用档位展示价。 */
    var realFen by mutableStateOf(0)
        private set
    val amountFen: Int get() = if (realFen > 0) realFen else plan.priceFen
    var steps by mutableStateOf(emptySteps())
        private set
    /** 本次吹风总秒数（进度条分母）。 */
    var total by mutableStateOf(0)
        private set

    /**
     * 吹风结束的墙钟时刻（epoch ms）。**倒计时只认这个值**：
     * 付完款直接退回桌面、切去微信、甚至把进程划掉，回来时剩下的时间还是对的。
     */
    var endAt by mutableStateOf(0L)
        private set

    /** 每秒抖一次，让下面几个派生值重新参与重组。 */
    private var tick by mutableStateOf(0L)

    var busy by mutableStateOf(false)
        private set
    var toast by mutableStateOf<String?>(null)

    val plan: Plan get() = plans.getOrElse(planIdx) { plans.first() }
    val channel: PayChannel get() = channels.getOrElse(channelIdx) { channels.first() }

    val running: Boolean
        get() {
            tick
            return endAt > System.currentTimeMillis()
        }

    val remain: Int
        get() {
            tick
            val left = endAt - System.currentTimeMillis()
            return if (left <= 0L) 0 else ((left + 999L) / 1000L).toInt()
        }

    val progress: Float
        get() {
            tick
            if (total <= 0) return 0f
            return ((total - remain).toFloat() / total).coerceIn(0f, 1f)
        }

    var repo: BreezeRepo? = (app as? BreezeApp)?.repo

    init {
        viewModelScope.launch {
            // 冷启动先把上次的 token 读回来；**必须验一次**——服务端 token 一小时就死，
            // 不验的话用户会先看到首页，然后一扫码才被告知「登录已过期」。
            val r = repo
            if (r != null && r.restoreSession()) {
                if (r.verifySession()) {
                    phone = maskPhone(r.phone)
                    phoneInput = r.lastPhone
                    restoreRun(r)
                    if (!running) screen = Screen.HOME
                } else {
                    r.logout()
                    phoneInput = r.lastPhone
                    toast = "登录已过期，请重新获取验证码"
                    screen = Screen.LOGIN
                }
            } else {
                r?.lastPhone?.takeIf { it.length == 11 }?.let { phoneInput = it }
                screen = Screen.LOGIN
            }
        }
        viewModelScope.launch {
            var wasRunning = false
            while (true) {
                delay(1000)
                if (codeCooldown > 0) codeCooldown -= 1
                tick = System.currentTimeMillis()
                if (wasRunning && endAt in 1..tick) {
                    // 到点了：清掉持久化，别让下次冷启动又恢复出一个 0 秒的会话
                    endAt = 0L
                    total = 0
                    repo?.clearRun()
                    toast = "吹风结束"
                }
                wasRunning = endAt > 0L
            }
        }
    }

    /**
     * 开始计时的唯一入口：把「结束时刻」固定下来并落盘。
     * [minutes] 是买到的分钟数，[no] 是订单号。
     */
    private fun beginRun(minutes: Int, no: String) {
        val sec = (minutes.coerceAtLeast(1)) * 60
        total = sec
        endAt = System.currentTimeMillis() + sec * 1000L
        tick = System.currentTimeMillis()
        val snapshot = StoredRun(
            orderNo = no.ifBlank { orderNo },
            deviceName = device.let { it.name + if (it.area.isBlank()) "" else " · " + it.area },
            minutes = minutes,
            priceFen = plan.priceFen,
            endAt = endAt,
            totalSec = sec,
        )
        repo?.let { r -> viewModelScope.launch { r.saveRun(snapshot) } }
    }

    /** 冷启动恢复：还没吹完就接着显示倒计时，已经吹完就什么都不做。 */
    private suspend fun restoreRun(r: BreezeRepo) {
        val run = r.loadRun() ?: return
        if (run.endAt <= System.currentTimeMillis()) {
            r.clearRun()
            return
        }
        orderNo = run.orderNo
        if (run.deviceName.isNotBlank()) {
            device = Device(id = device.id, name = run.deviceName, area = "", status = "设备在线 · 吹风中")
        }
        if (run.minutes > 0) {
            plans = listOf(Plan(minutes = run.minutes, priceFen = run.priceFen, tier = true))
            planIdx = 0
        }
        total = if (run.totalSec > 0) run.totalSec else run.minutes * 60
        endAt = run.endAt
        tick = System.currentTimeMillis()
        screen = Screen.DONE
    }

    // ---- 登录 ----
    fun onPhoneChange(v: String) {
        phoneInput = v.filter { it.isDigit() }.take(11)
    }

    fun onCodeChange(v: String) {
        codeInput = v.filter { it.isDigit() }.take(6)
    }

    fun sendCode() {
        val r = repo
        val p = phoneInput
        if (p.length < 11) {
            toast = "请输入 11 位手机号"
            return
        }
        if (codeCooldown > 0 || busy) return
        if (r == null) {
            codeSent = true
            codeCooldown = 60
            toast = "演示模式：随便填 4 位"
            return
        }
        busy = true
        viewModelScope.launch {
            runCatching { r.sendCode(p) }.fold(
                onSuccess = { err ->
                    if (err == null) {
                        codeSent = true
                        codeCooldown = 60
                        toast = "验证码已发送"
                    } else {
                        toast = err
                    }
                },
                onFailure = { toast = "发送失败：" + (it.message ?: "网络异常") },
            )
            busy = false
        }
    }

    fun login() {
        val r = repo
        val p = phoneInput
        val c = codeInput
        if (p.length < 11) {
            toast = "请输入 11 位手机号"
            return
        }
        if (c.length < 4) {
            toast = "请输入短信验证码"
            return
        }
        if (r == null) {
            phone = maskPhone(p)
            screen = Screen.HOME
            return
        }
        busy = true
        viewModelScope.launch {
            runCatching { r.login(p, c) }.fold(
                onSuccess = { err ->
                    if (err == null) {
                        phone = maskPhone(r.phone.ifBlank { p })
                        screen = Screen.HOME
                    } else {
                        toast = err
                    }
                },
                onFailure = { toast = "登录失败：" + (it.message ?: "网络异常") },
            )
            busy = false
        }
    }

    fun logout() {
        viewModelScope.launch { repo?.logout() }
        phoneInput = ""
        codeInput = ""
        codeSent = false
        phone = ""
        screen = Screen.LOGIN
    }

    /** 登录过期时弹窗显示的原因（接口名），null = 不弹。 */
    var loginExpired by mutableStateOf<String?>(null)
        private set

    /** token 过期（服务端 code=2）：**不清登录态**，只提示，由用户决定要不要重新登录。 */
    private fun expireSession(path: String) {
        loginExpired = path
        // 把号码预填好，用户点「重新登录」只需要收一条验证码
        repo?.lastPhone?.takeIf { it.length == 11 }?.let { phoneInput = it }
        toast = "登录已过期（$path），需要重新收验证码登录"
    }

    fun dismissExpired() {
        loginExpired = null
    }

    /** 弹窗里点「重新登录」：这时才真正清掉本地会话，但保留手机号方便再收一条码。 */
    fun relogin() {
        val keep = phoneInput
        loginExpired = null
        logout()
        phoneInput = keep
    }

    /** 统一的失败兜底：过期只提示，其它就当普通错误提示。 */
    private fun failWith(e: Throwable, prefix: String = "") {
        if (e is SessionExpired) {
            expireSession(e.path)
        } else {
            toast = prefix + (e.message?.takeIf { it.isNotBlank() && it != "null" } ?: "网络异常")
        }
    }

    private fun maskPhone(p: String): String =
        if (p.length >= 11) p.substring(0, 3) + "****" + p.substring(7) else p

    // ---- 导航 ----
    fun openScan() {
        screen = Screen.SCAN
    }

    fun backHome() {
        screen = Screen.HOME
    }

    fun backToPay() {
        if (screen == Screen.SCAN) screen = Screen.PAY
        if (screen == Screen.DONE) screen = Screen.PAY
    }

    /** 首页「吹风中」入口：回到倒计时页。 */
    fun openRunning() {
        if (running) screen = Screen.DONE
    }

    /**
     * 完成页按返回：正在吹就退回首页但**不停表**（计时是墙钟的，切走不丢，
     * 首页顶部会留一个「吹风中」入口）；没在吹就是收尾，直接结束。
     */
    fun backFromDone() {
        if (running) screen = Screen.HOME else stop()
    }

    fun choosePlan(i: Int) {
        if (i in plans.indices) {
            planIdx = i
            quotePrice()
        }
    }

    /** 换档位时向服务端问一次实付金额（preview/v4 的 realPrice），合计按它显示。 */
    private fun quotePrice() {
        val r = repo ?: return
        val d = device
        val p = plan
        viewModelScope.launch {
            runCatching { r.previewPrice(d, p) }
                .onSuccess { fen -> if (plan.minutes == p.minutes) realFen = fen }
                .onFailure { realFen = 0 }
        }
    }

    fun chooseChannel(i: Int) {
        val c = channels.getOrNull(i) ?: return
        if (!c.enabled) {
            toast = "该渠道暂不可用：${c.name}"
            return
        }
        channelIdx = i
    }

    // ---- 扫码 / 手动输入 ----
    fun onScanned(raw: String) {
        val r = repo
        if (r == null) {
            applyDemo(raw)
            return
        }
        busy = true
        viewModelScope.launch {
            runCatching { r.loadByCode(raw) }
                .onSuccess { res ->
                    device = res.device
                    plans = res.plans
                    channels = res.channels
                    planIdx = plans.indexOfFirst { it.badge != null }.takeIf { it >= 0 } ?: 0
                    // 支付宝优先，其次微信；都没有就退回第一个可选渠道
                    channelIdx = channels.indexOfFirst { it.code == 13 && it.enabled }
                        .takeIf { it >= 0 }
                        ?: channels.indexOfFirst { it.code == 32 && it.enabled }.takeIf { it >= 0 }
                        ?: channels.indexOfFirst { it.enabled }.takeIf { it >= 0 }
                        ?: 0
                    realFen = 0
                    ticket = res.ticket ?: ticket
                    screen = Screen.PAY
                    quotePrice()
                }
                .onFailure { e ->
                    if (e is SessionExpired) {
                        failWith(e)
                    } else {
                        toast = "读取设备失败，已用示例数据：" + (e.message ?: "网络异常")
                        applyDemo(raw)
                    }
                }
            busy = false
        }
    }

    private fun applyDemo(raw: String) {
        val id = extractDeviceId(raw)
        device = Device(
            id = id,
            name = "吹风机",
            area = "A 区 3 号",
            status = "设备在线 · 空闲",
        )
        screen = Screen.PAY
    }

    private fun extractDeviceId(raw: String): String {
        // 二维码通常是 URL 或纯设备号；取最后一段非空内容
        val t = raw.trim()
        val tail = t.substringAfterLast('/').substringBefore('?')
        return tail.ifBlank { t }.take(32)
    }

    // ---- 支付 + 启动 ----
    /**
     * 真实链路：trade/create → pay/unify/prePay → 支付宝/微信 SDK → order/pay_status
     *          → trade/order/start → machine/device_is_start。
     *
     * 任何一步失败都停在「完成」页对应步骤上并 toast，不会假装成功。
     * 没有 repo（演示模式）时走 [demoPay]。
     */
    fun pay(activity: android.app.Activity) {
        if (busy) return
        val p = plan
        val c = channel
        busy = true
        orderNo = ""
        val which = when (c.code) {
            13 -> "支付宝"
            32 -> "微信"
            else -> c.name
        }
        steps = listOf(
            StepState("订单已创建", "正在提交订单…", StepPhase.DOING),
            StepState("支付成功", "等待拉起$which", StepPhase.WAIT),
            StepState("正在启动设备", "等待设备响应", StepPhase.WAIT),
            StepState("开始吹风", "倒计时结束后自动停机", StepPhase.WAIT),
        )
        total = p.minutes * 60
        endAt = 0L
        screen = Screen.DONE

        val r = repo
        if (r == null) {
            demoPay(p, c)
            return
        }

        viewModelScope.launch {
            try {
                val (no, fen) = r.createOrder(device, p)
                orderNo = no
                realFen = fen
                setStep(0, StepPhase.DONE, "NO.$no")

                val paid = when (c.code) {
                    13 -> payByAlipay(r, activity, no, c.code)
                    32 -> payByWechat(r, activity, no, c.code)
                    else -> {
                        setStep(1, StepPhase.WAIT, "该渠道暂未接入")
                        toast = "该渠道暂未接入：${c.name}"
                        false
                    }
                }
                if (!paid) {
                    busy = false
                    return@launch
                }
                setStep(1, StepPhase.DONE, "${c.name} · ¥${formatFen(fen)}")
                // 付款成功这一刻就锁死结束时刻：接下来去打启动接口、以及用户
                // 从支付宝切回来的那几十秒，都算在吹风时间里（设备确实在吹）。
                beginRun(p.minutes, no)

                setStep(2, StepPhase.DOING, "已下发启动指令…")
                r.startDevice(no)
                var started = false
                for (i in 0 until 10) {
                    delay(1000)
                    if (r.deviceIsStart(no)) { started = true; break }
                }
                setStep(
                    2,
                    StepPhase.DONE,
                    if (started) "设备已启动" else "启动指令已发送，设备暂未回应",
                )
                setStep(3, StepPhase.DONE, "倒计时结束后自动停机")
                if (!started) toast = "设备暂时没回应，请检查是否已通电"
            } catch (e: Exception) {
                val msg = if (e is SessionExpired) {
                    "登录已过期，请重新登录"
                } else {
                    e.message?.takeIf { it.isNotBlank() && it != "null" } ?: "网络异常"
                }
                val idx = steps.indexOfFirst { it.phase == StepPhase.DOING }
                if (idx >= 0) setStep(idx, StepPhase.WAIT, msg)
                failWith(e, "支付失败：")
            }
            busy = false
        }
    }

    /** 拉起支付宝，再等服务端确认。 */
    private suspend fun payByAlipay(r: BreezeRepo, activity: android.app.Activity, no: String, code: Int): Boolean {
        setStep(1, StepPhase.DOING, "正在拉起支付宝…")
        val info = r.alipayOrderInfo(no, code)
        val status = Alipay.pay(activity, info)
        if (status != Alipay.STATUS_SUCCESS) {
            val hint = Alipay.hint(status)
            setStep(1, StepPhase.WAIT, hint)
            toast = hint
            return false
        }
        return confirmPaid(r, no, code)
    }

    /** 拉起微信（结果由 WXEntryActivity 回传），再等服务端确认。 */
    private suspend fun payByWechat(r: BreezeRepo, activity: android.app.Activity, no: String, code: Int): Boolean {
        setStep(1, StepPhase.DOING, "正在拉起微信…")
        val json = r.wechatOrderInfo(no, code)
        val waiter = Wechat.newWaiter()
        val err = Wechat.send(activity, json)
        if (err != null) {
            setStep(1, StepPhase.WAIT, err)
            toast = err
            return false
        }
        val result = kotlinx.coroutines.withTimeoutOrNull(180_000) { waiter.await() }
        if (result != 0) {
            val hint = Wechat.hint(result)
            setStep(1, StepPhase.WAIT, hint)
            toast = hint
            return false
        }
        return confirmPaid(r, no, code)
    }

    /** SDK 说成功还不够，等一次服务端回调确认。 */
    private suspend fun confirmPaid(r: BreezeRepo, no: String, code: Int): Boolean {
        setStep(1, StepPhase.DOING, "确认支付结果…")
        for (i in 0 until 6) {
            delay(1200)
            if (r.payStatus(no, code)) return true
        }
        setStep(1, StepPhase.WAIT, "已付款，服务端还在确认…")
        toast = "已付款，正在等商家确认"
        return false
    }

    private fun demoPay(p: Plan, c: PayChannel) {
        orderNo = buildOrderNo()
        steps = listOf(
            StepState("订单已创建", "NO.$orderNo", StepPhase.DONE),
            StepState("支付成功", "${c.name} · ${p.priceText}", StepPhase.WAIT),
            StepState("正在启动设备", "等待设备响应…", StepPhase.WAIT),
            StepState("开始吹风", "倒计时结束后自动停机", StepPhase.WAIT),
        )
        viewModelScope.launch {
            delay(700)
            setStep(1, StepPhase.DONE, "${c.name} · ${p.priceText}")
            // 演示模式也从「支付成功」这一刻起算
            beginRun(p.minutes, orderNo)
            delay(900)
            setStep(2, StepPhase.DOING, "已下发启动指令…")
            delay(1500)
            setStep(2, StepPhase.DONE, "已下发启动指令")
            setStep(3, StepPhase.DONE, "倒计时结束后自动停机")
            busy = false
        }
    }

    private fun setStep(index: Int, phase: StepPhase, sub: String? = null) {
        steps = steps.toMutableList().also {
            if (index in it.indices) {
                it[index] = it[index].copy(phase = phase, sub = sub ?: it[index].sub)
            }
        }
    }

    fun stop() {
        endAt = 0L
        total = 0
        repo?.let { r -> viewModelScope.launch { r.clearRun() } }
        screen = Screen.HOME
        toast = "已结束吹风"
    }

    fun resetToHome() {
        endAt = 0L
        total = 0
        repo?.let { r -> viewModelScope.launch { r.clearRun() } }
        orderNo = ""
        steps = emptySteps()
        screen = Screen.HOME
    }

    // ---- 历史订单 ----
    var orders by mutableStateOf<List<OrderRecord>>(emptyList())
        private set
    var ordersBusy by mutableStateOf(false)
        private set
    var ordersLoaded by mutableStateOf(false)
        private set

    fun openHistory() {
        screen = Screen.HISTORY
        loadOrders()
    }

    fun loadOrders() {
        val r = repo ?: run {
            ordersLoaded = true
            toast = "演示模式：没有订单"
            return
        }
        if (ordersBusy) return
        ordersBusy = true
        viewModelScope.launch {
            runCatching { r.orders(1, 20) }.fold(
                onSuccess = {
                    orders = it
                    ordersLoaded = true
                },
                onFailure = {
                    failWith(it, "订单加载失败：")
                    ordersLoaded = true
                },
            )
            ordersBusy = false
        }
    }

    private fun buildOrderNo(): String {
        val now = java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.US).format(java.util.Date())
        val rand = (1000..9999).random()
        return "$now$rand"
    }

    companion object {
        fun emptySteps() = listOf(
            StepState("订单已创建", "等待下单", StepPhase.WAIT),
            StepState("支付成功", "等待支付", StepPhase.WAIT),
            StepState("正在启动设备", "等待设备响应", StepPhase.WAIT),
            StepState("开始吹风", "倒计时结束后自动停机", StepPhase.WAIT),
        )
    }
}

fun previewPlanText(p: Plan) = p.priceText
