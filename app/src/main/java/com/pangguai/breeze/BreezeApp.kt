package com.pangguai.breeze

import android.app.Application
import com.pangguai.breeze.data.Api
import com.pangguai.breeze.data.BreezeRepo
import com.pangguai.breeze.data.Session

class BreezeApp : Application() {
    val api: Api by lazy { Api() }
    val session: Session by lazy { Session(this) }
    val repo: BreezeRepo by lazy { BreezeRepo(api, session) }
}
