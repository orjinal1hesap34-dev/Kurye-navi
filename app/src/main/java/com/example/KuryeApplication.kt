package com.example

import android.app.Application
import com.example.data.MapboxConfig

class KuryeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MapboxConfig.initMapLibre(this)
    }
}
