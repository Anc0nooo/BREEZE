package com.pangguai.breeze.debug.wxapi

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.pangguai.breeze.data.Wechat
import com.tencent.mm.opensdk.modelbase.BaseReq
import com.tencent.mm.opensdk.modelbase.BaseResp
import com.tencent.mm.opensdk.openapi.IWXAPI
import com.tencent.mm.opensdk.openapi.IWXAPIEventHandler
import com.tencent.mm.opensdk.openapi.WXAPIFactory

/**
 * 微信回调入口。类名和包名是微信 SDK 写死的约定：
 * `<applicationId>.wxapi.WXEntryActivity`，所以 debug 变体（applicationId 带 .debug）要单独一份。
 */
class WXEntryActivity : Activity(), IWXAPIEventHandler {

    private var api: IWXAPI? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = WXAPIFactory.createWXAPI(this, null)
        runCatching { api?.handleIntent(intent, this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        runCatching { api?.handleIntent(intent, this) }
    }

    override fun onReq(req: BaseReq?) = Unit

    override fun onResp(resp: BaseResp?) {
        if (resp is com.tencent.mm.opensdk.modelpay.PayResp) {
            Wechat.fromCallback(resp.errCode)
        }
        finish()
    }
}
