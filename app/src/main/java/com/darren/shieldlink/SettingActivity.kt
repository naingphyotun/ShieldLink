package com.darren.shieldlink

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 0, 32, 32)
            setBackgroundColor(resources.getColor(R.color.surface, theme))
        }

        val toolbar = Toolbar(this).apply {
            title = "Settings"
            setBackgroundColor(resources.getColor(R.color.purple_primary, theme))
            setTitleTextColor(resources.getColor(R.color.white, theme))
        }
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        root.addView(toolbar)

        val prefs = getSharedPreferences("shieldlink_settings", MODE_PRIVATE)
        val currentMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        val radioGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(0, 40, 0, 0)
        }

        val lightOption = RadioButton(this).apply {
            text = "Light mode"
            id = 1
            setTextColor(resources.getColor(R.color.text_primary, theme))
        }
        val darkOption = RadioButton(this).apply {
            text = "Dark mode"
            id = 2
            setTextColor(resources.getColor(R.color.text_primary, theme))
        }
        val systemOption = RadioButton(this).apply {
            text = "Follow system"
            id = 3
            setTextColor(resources.getColor(R.color.text_primary, theme))
        }

        radioGroup.addView(lightOption)
        radioGroup.addView(darkOption)
        radioGroup.addView(systemOption)

        when (currentMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> lightOption.isChecked = true
            AppCompatDelegate.MODE_NIGHT_YES -> darkOption.isChecked = true
            else -> systemOption.isChecked = true
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val newMode = when (checkedId) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            prefs.edit().putInt("theme_mode", newMode).apply()
            AppCompatDelegate.setDefaultNightMode(newMode)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        root.addView(radioGroup)
        setContentView(root)
        root.fitsSystemWindows = true
    }
}