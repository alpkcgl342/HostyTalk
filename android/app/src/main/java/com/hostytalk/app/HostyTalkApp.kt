package com.hostytalk.app

import android.app.Application
import com.hostytalk.app.data.Api
import com.hostytalk.app.data.Prefs

class HostyTalkApp : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var api: Api
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        api = Api(prefs)
    }
}
