package com.baran.jarvis

import android.app.Application

class JarvisApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TermuxSession.init(this)
    }
}
