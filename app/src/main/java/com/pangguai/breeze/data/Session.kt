package com.pangguai.breeze.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.breezeStore by preferencesDataStore(name = "breeze_session")

/**
 * 正在进行的吹风会话。
 *
 * 倒计时必须锚在「墙钟时间」上，而不是 App 进程里的递减计数器：
 * 用户付完款可能直接退回桌面、切去看微信，甚至把进程划掉，
 * 这段时间设备照样在吹，所以只存一个「结束时刻」[endAt]，其余全靠算。
 */
data class StoredRun(
    val orderNo: String = "",
    val deviceName: String = "",
    val minutes: Int = 0,
    val priceFen: Int = 0,
    val endAt: Long = 0L,
    val totalSec: Int = 0,
)

/**
 * 登录态 + 吹风会话持久化。存 DataStore，杀进程、重启手机都还在。
 */
class Session(private val context: Context) {

    private object K {
        val token = stringPreferencesKey("token")
        val phone = stringPreferencesKey("phone")
        val runOrderNo = stringPreferencesKey("run_order_no")
        val runDevice = stringPreferencesKey("run_device")
        val runMinutes = longPreferencesKey("run_minutes")
        val runPrice = longPreferencesKey("run_price")
        val runEndAt = longPreferencesKey("run_end_at")
        val runTotal = longPreferencesKey("run_total")
    }

    suspend fun load(): Pair<String, String> {
        val p = context.breezeStore.data.first()
        return (p[K.token] ?: "") to (p[K.phone] ?: "")
    }

    suspend fun save(token: String, phone: String) {
        context.breezeStore.edit {
            it[K.token] = token
            it[K.phone] = phone
        }
    }

    suspend fun clear() {
        context.breezeStore.edit {
            it.remove(K.token)
            it.remove(K.phone)
        }
    }

    suspend fun loadRun(): StoredRun? {
        val p = context.breezeStore.data.first()
        val endAt = p[K.runEndAt] ?: 0L
        if (endAt <= 0L) return null
        return StoredRun(
            orderNo = p[K.runOrderNo] ?: "",
            deviceName = p[K.runDevice] ?: "",
            minutes = (p[K.runMinutes] ?: 0L).toInt(),
            priceFen = (p[K.runPrice] ?: 0L).toInt(),
            endAt = endAt,
            totalSec = (p[K.runTotal] ?: 0L).toInt(),
        )
    }

    suspend fun saveRun(r: StoredRun) {
        context.breezeStore.edit {
            it[K.runOrderNo] = r.orderNo
            it[K.runDevice] = r.deviceName
            it[K.runMinutes] = r.minutes.toLong()
            it[K.runPrice] = r.priceFen.toLong()
            it[K.runEndAt] = r.endAt
            it[K.runTotal] = r.totalSec.toLong()
        }
    }

    suspend fun clearRun() {
        context.breezeStore.edit {
            it.remove(K.runOrderNo)
            it.remove(K.runDevice)
            it.remove(K.runMinutes)
            it.remove(K.runPrice)
            it.remove(K.runEndAt)
            it.remove(K.runTotal)
        }
    }
}
