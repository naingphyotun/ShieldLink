package com.darren.shieldlink

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

class SafeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
    }

    private lateinit var checkedUrl: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkedUrl = intent.getStringExtra(EXTRA_URL) ?: "unknown link"

        val webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.addJavascriptInterface(SafeBridge(), "Android")
        webView.loadUrl("file:///android_asset/safe_page.html")

        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                webView.evaluateJavascript("setUrl(${org.json.JSONObject.quote(checkedUrl)})", null)
            }
        }

    }

    inner class SafeBridge {
        @JavascriptInterface
        fun continueToLink() {
            runOnUiThread {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(checkedUrl))
                startActivity(browserIntent)
                finish()
            }
        }
    }
}