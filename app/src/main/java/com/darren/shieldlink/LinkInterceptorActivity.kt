package com.darren.shieldlink

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.ComponentActivity

class LinkInterceptorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ckecking) // ← show something immediately

        val url: String? = intent?.data?.toString()

        if (url.isNullOrBlank()) {
            finish()
            return
        }

        checkAndResolve(url)
    }

    private fun checkAndResolve(url: String) {
        val startTime = System.currentTimeMillis()

        // Local blocklist check first - instant, no network call needed.
        if (GamblingFilter.isGamblingDomain(this, url)) {
            val elapsed = System.currentTimeMillis() - startTime
            android.util.Log.d("ShieldLinkLatency", "url=$url verdict=RESTRICTED latency_ms=$elapsed")

            val warningIntent = Intent(this, WarningActivity::class.java).apply {
                putExtra(WarningActivity.EXTRA_URL, url)
                putExtra(WarningActivity.EXTRA_CATEGORY, "gambling")
            }
            startActivity(warningIntent)
            finish()
            return
        }

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    VirusTotalClient.checkUrlDetailed(this@LinkInterceptorActivity, url)
                } catch (e: Exception) {
                    null
                }
            }

            val elapsed = System.currentTimeMillis() - startTime

            if (result == null) {
                android.util.Log.d("ShieldLinkLatency", "url=$url verdict=ERROR latency_ms=$elapsed")
                openInRealBrowser(url)
                finish()
                return@launch
            }

            android.util.Log.d("ShieldLinkLatency", "url=$url verdict=${result.verdict} latency_ms=$elapsed")

            when (result.verdict) {
                LinkVerdict.MALICIOUS -> {
                    val warningIntent = Intent(this@LinkInterceptorActivity, WarningActivity::class.java).apply {
                        putExtra(WarningActivity.EXTRA_URL, url)
                        putExtra(WarningActivity.EXTRA_CATEGORY, "malicious")
                    }
                    startActivity(warningIntent)
                }
                LinkVerdict.RESTRICTED -> {
                    val warningIntent = Intent(this@LinkInterceptorActivity, WarningActivity::class.java).apply {
                        putExtra(WarningActivity.EXTRA_URL, url)
                        putExtra(WarningActivity.EXTRA_CATEGORY, "gambling")
                    }
                    startActivity(warningIntent)
                }
                LinkVerdict.SAFE, LinkVerdict.UNKNOWN -> openInRealBrowser(url)
            }
            finish()
        }
    }

    private fun openInRealBrowser(url: String) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                setPackage(getDefaultBrowserPackage())
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(browserIntent)
            moveTaskToBack(true)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open link: $url", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getDefaultBrowserPackage(): String? {
        val resolveIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
        val resolveInfo = packageManager.resolveActivity(
            resolveIntent,
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
        )
        val pkg = resolveInfo?.activityInfo?.packageName
        return if (pkg == null || pkg == packageName) "com.android.chrome" else pkg
    }
}