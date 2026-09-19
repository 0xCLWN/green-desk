package com.green.android

import android.app.Application
import go.Seq

class GreenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Seq.setContext(this)
    }
}
