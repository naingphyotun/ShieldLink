package com.darren.shieldlink

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

class WarningActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_RETURN_HOME = "extra_return_home" // true = came from inside the app (QR scan)
        const val EXTRA_CATEGORY = "extra_category"
    }


    private lateinit var blockedUrl: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        blockedUrl = intent.getStringExtra(EXTRA_URL) ?: "unknown link"

        val webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.addJavascriptInterface(WarningBridge(), "Android")
        webView.loadUrl("file:///android_asset/warning_page.html")

        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                webView.evaluateJavascript("setUrl(${org.json.JSONObject.quote(blockedUrl)})", null)
            }
        }

        logAttempt(blockedUrl, "MALICIOUS")
    }

    /**
     * Bridge between the HTML/JS buttons and real Kotlin code.
     * @JavascriptInterface exposes these two functions so the WebView's
     * JS can call Android.goBack() / Android.proceedAnyway().
     */
    inner class WarningBridge {
        @JavascriptInterface
        fun goBack() {
            runOnUiThread {
                val returnHome = intent.getBooleanExtra(EXTRA_RETURN_HOME, false)
                if (returnHome) {
                    val homeIntent = Intent(this@WarningActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    startActivity(homeIntent)
                } else {
                    moveTaskToBack(true)
                }
                finish()
            }
        }

        @JavascriptInterface
        fun proceedAnyway() {
            runOnUiThread {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(blockedUrl))
                startActivity(browserIntent)
                finish()
            }
        }
    }

    /** Placeholder for Phase 6's history screen - stores blocked links locally. */
    private fun logAttempt(url: String, verdict: String) {
        val category = intent.getStringExtra(EXTRA_CATEGORY) ?: "malware"
        val prefs = getSharedPreferences("shieldlink_history", MODE_PRIVATE)
        val existing = prefs.getStringSet("link_history", mutableSetOf()) ?: mutableSetOf()
        val updated = existing.toMutableSet()
        updated.add("${System.currentTimeMillis()}|$verdict|$url")
        prefs.edit().putStringSet("link_history", updated).apply()
    }
}