package com.darren.shieldlink

import android.os.Bundle
import android.app.role.RoleManager
import android.content.Intent
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.drawerlayout.widget.DrawerLayout
import com.darren.shieldlink.R
import com.google.android.material.navigation.NavigationView
import androidx.appcompat.app.AlertDialog
import android.graphics.Color
import android.os.Build
import android.view.View


class MainActivity : AppCompatActivity() {

    private lateinit var statusIcon: TextView
    private lateinit var statusTitle: TextView
    private lateinit var statusSubtitle: TextView
    private lateinit var browserCheckIcon: TextView
    private lateinit var apiCheckIcon: TextView
    private lateinit var setupButton: Button
    private lateinit var helperText: TextView

    companion object {
        private const val REQUEST_BROWSER_ROLE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()   // must be called before super.onCreate()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val navView = findViewById<NavigationView>(R.id.navView)

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.nav_open, R.string.nav_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> { /* already home, do nothing for now */ }
                R.id.nav_history -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, ApiKeySetupActivity::class.java))
                }
                R.id.nav_manual_check -> {
                    startActivity(Intent(this, CheckLinkActivity::class.java))
                }
                R.id.nav_qr_scan -> {
                    startActivity(Intent(this, QrScanActivity::class.java))
                }
            }
            drawerLayout.closeDrawers()
            true
        }

        // Status card setup
        statusIcon = findViewById(R.id.statusIcon)
        statusTitle = findViewById(R.id.statusTitle)
        statusSubtitle = findViewById(R.id.statusSubtitle)
        browserCheckIcon = findViewById(R.id.browserCheckIcon)
        apiCheckIcon = findViewById(R.id.apiCheckIcon)
        setupButton = findViewById(R.id.setupButton)
        helperText = findViewById(R.id.helperText)

        updateStatusCard()
    }

    override fun onResume() {
        super.onResume()
        updateStatusCard()   // refresh after returning from the key screen or default-browser dialog
    }

    private fun updateStatusCard() {
        val isDefault = isDefaultBrowser()
        val hasKey = ApiKeyStore.hasKey(this)
        val grey = resources.getColor(android.R.color.darker_gray, theme)
        val green = resources.getColor(R.color.status_success, theme)

        browserCheckIcon.text = if (isDefault) "✓" else "○"
        browserCheckIcon.setTextColor(if (isDefault) green else grey)

        apiCheckIcon.text = if (hasKey) "✓" else "○"
        apiCheckIcon.setTextColor(if (hasKey) green else grey)

        when {
            !hasKey -> {
                statusIcon.text = "⚠"
                statusTitle.text = "Setup needed"
                statusTitle.setTextColor(resources.getColor(R.color.status_warning, theme))
                statusSubtitle.text = "Step 1: add your VirusTotal key"
                helperText.text = "ShieldLink uses your own free VirusTotal key to check links for malware. " +
                        "Tap the button, follow the steps, and paste your key. Gambling sites are blocked even without it."
                setupButton.text = "Add VirusTotal key"
                setupButton.visibility = View.VISIBLE
                setupButton.setOnClickListener {
                    startActivity(Intent(this, ApiKeySetupActivity::class.java))
                }
            }
            !isDefault -> {
                statusIcon.text = "⚠"
                statusTitle.text = "Almost there"
                statusTitle.setTextColor(resources.getColor(R.color.status_warning, theme))
                statusSubtitle.text = "Step 2: make ShieldLink your link handler"
                helperText.text = "Choose ShieldLink as your default browser so every link you tap " +
                        "is checked before it opens."
                setupButton.text = "Set as default browser"
                setupButton.visibility = View.VISIBLE
                setupButton.setOnClickListener { requestDefaultBrowserRole() }
            }
            else -> {
                statusIcon.text = "🛡"
                statusTitle.text = "ShieldLink is protecting you"
                statusTitle.setTextColor(green)
                statusSubtitle.text = "All links are being checked automatically"
                helperText.text = "All set. Tap any link in any app and ShieldLink checks it before it opens. " +
                        "Gambling sites are blocked and unsafe links are flagged."
                setupButton.visibility = View.GONE
            }
        }
    }

    private fun isDefaultBrowser(): Boolean {
        val roleManager = getSystemService(RoleManager::class.java)
        return roleManager?.isRoleHeld(RoleManager.ROLE_BROWSER) == true
    }

    private fun requestDefaultBrowserRole() {
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
            startActivityForResult(intent, REQUEST_BROWSER_ROLE)
        } else {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }
    }

    // Kept for the future "Advanced / Experimental" section covering the
    // AccessibilityService pathway - not called from the main flow anymore.
    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = "$packageName/${LinkWatcherService::class.java.canonicalName}"
        val enabledServices = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { it.equals(expectedComponentName, ignoreCase = true) }
    }
}