package com.lite.launcher

import android.app.Application
import android.content.Intent
import android.os.Build
import android.provider.Settings

class LiteApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) return
        try {
            val i = Intent(this, ControlCenterOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        } catch (_: Exception) {}
    }
}
