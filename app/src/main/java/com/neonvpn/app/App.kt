package com.neonvpn.app

import android.app.Application
import org.amnezia.awg.backend.GoBackend

class App : Application() {

    val backend: GoBackend by lazy { GoBackend(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
