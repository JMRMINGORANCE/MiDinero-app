package com.midinero.control.wear

import android.app.Application

class WatchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WatchStore.init(this)
    }
}
