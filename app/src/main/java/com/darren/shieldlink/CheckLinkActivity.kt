package com.darren.shieldlink

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CheckLinkActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_check_link)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.checkToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // adds a back arrow
        toolbar.setNavigationOnClickListener { finish() }

        val urlInput = findViewById<EditText>(R.id.urlInput)
        val checkButton = findViewById<Button>(R.id.checkButton)
        val loadingSpinner = findViewById<ProgressBar>(R.id.loadingSpinner)
        val verdictBanner = findViewById<TextView>(R.id.verdictBanner)
        val engineListContainer = findViewById<LinearLayout>(R.id.engineListContainer)

        checkButton.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isBlank()) {
                Toast.makeText(this, "Enter a link first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Reset UI for a new check
            verdictBanner.visibility = android.view.View.GONE
            engineListContainer.removeAllViews()
            loadingSpinner.visibility = android.view.View.VISIBLE
            checkButton.isEnabled = false

            // The network call is blocking, so it must run on Dispatchers.IO,
            // never on the main thread (that would freeze the UI).
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    try {
                        VirusTotalClient.checkUrlDetailed(this@CheckLinkActivity, url)
                    } catch (e: Exception) {
                        null
                    }
                }

                loadingSpinner.visibility = android.view.View.GONE
                checkButton.isEnabled = true

                if (result == null) {
                    Toast.makeText(this@CheckLinkActivity, "Couldn't reach VirusTotal", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                displayResult(result, url, verdictBanner, engineListContainer)
            }
        }
    }

    private fun displayResult(
        result: ScanResult,
        checkedUrl: String,
        verdictBanner: TextView,
        container: LinearLayout
    ) {
        verdictBanner.visibility = android.view.View.VISIBLE
        when (result.verdict) {
            LinkVerdict.SAFE -> {
                verdictBanner.text = "✅ SAFE — ${result.totalEnginesChecked} engines checked, 0 flagged it"
                verdictBanner.setBackgroundColor(Color.parseColor("#2E7D32")) // green
            }
            LinkVerdict.MALICIOUS -> {
                verdictBanner.text = "⚠ MALICIOUS — ${result.maliciousCount + result.suspiciousCount} of " +
                        "${result.totalEnginesChecked} engines flagged this link"
                verdictBanner.setBackgroundColor(Color.parseColor("#D32F2F"))
            }
            LinkVerdict.RESTRICTED -> {
                verdictBanner.text = "🚫 RESTRICTED — this link leads to a gambling site"
                verdictBanner.setBackgroundColor(Color.parseColor("#F57C00")) // orange, distinct from red "malicious"
            }
            LinkVerdict.UNKNOWN -> {
                verdictBanner.text = "❓ Couldn't get a result yet — try again in a moment"
                verdictBanner.setBackgroundColor(Color.parseColor("#757575")) // gray
            }
        }

        // Build one row per flagged engine, showing WHICH engine and WHY
        for (engine in result.flaggedEngines) {
            val row = TextView(this).apply {
                text = "• ${engine.engineName}: ${engine.result} (${engine.category})"
                textSize = 15f
                setPadding(12, 12, 12, 12)
                gravity = Gravity.START
            }
            container.addView(row)
        }
    }
}