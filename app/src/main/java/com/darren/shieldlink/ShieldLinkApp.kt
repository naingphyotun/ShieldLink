package com.darren.shieldlink

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class ShieldLinkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("shieldlink_settings", MODE_PRIVATE)
        val savedMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedMode)
    }
}