package com.darren.shieldlink

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat

/**
 * Screen where the user gets, pastes and tests their own VirusTotal API key.
 * Part 1: how to get a key.  Part 2: paste and save.  Part 3: testing ground.
 */
class ApiKeySetupActivity : AppCompatActivity() {

    private lateinit var keyInput: TextInputEditText
    private lateinit var keyStatus: TextView
    private lateinit var savedKeyText: TextView
    private lateinit var testUrlInput: EditText
    private lateinit var testResult: TextView
    private lateinit var saveButton: Button
    private lateinit var testButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_api_key_setup)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val root = findViewById<android.view.View>(R.id.scrollRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(0, bars.top, 0, maxOf(bars.bottom, ime.bottom))
            insets
        }
        title = getString(R.string.vt_setup_title)

        keyInput = findViewById(R.id.keyInput)
        keyStatus = findViewById(R.id.keyStatus)
        savedKeyText = findViewById(R.id.savedKeyText)
        testUrlInput = findViewById(R.id.testUrlInput)
        testResult = findViewById(R.id.testResult)
        saveButton = findViewById(R.id.saveKeyButton)
        testButton = findViewById(R.id.testLinkButton)

        findViewById<Button>(R.id.openSignUpButton).setOnClickListener {
            openPage("https://www.virustotal.com/gui/join-us")
        }
        findViewById<Button>(R.id.openKeyPageButton).setOnClickListener {
            openPage("https://www.virustotal.com/gui/my-apikey")
        }
        findViewById<Button>(R.id.pasteButton).setOnClickListener { pasteFromClipboard() }
        saveButton.setOnClickListener { saveAndCheckKey() }
        findViewById<Button>(R.id.removeKeyButton).setOnClickListener { removeKey() }
        testButton.setOnClickListener { runTest() }

        refreshSavedState()
        val scroll = root as android.widget.ScrollView
        testUrlInput.setOnFocusChangeListener { _, focused ->
            if (focused) scroll.postDelayed({
                scroll.smoothScrollTo(0, scroll.getChildAt(0).bottom)
            }, 300)
        }
    }

    // ---- Part 1: open VirusTotal in the user's browser ----------------------------------

    private fun openPage(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    // ---- Part 2: paste, check, save ------------------------------------------------------

    private fun pasteFromClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
        if (text.isNullOrEmpty()) {
            setStatus(getString(R.string.vt_clipboard_empty), isError = true)
        } else {
            keyInput.setText(text)
            keyInput.setSelection(text.length)
        }
    }

    private fun saveAndCheckKey() {
        val candidate = keyInput.text?.toString()?.trim().orEmpty()
        // A VirusTotal key is 64 hexadecimal characters
        if (!Regex("^[0-9a-fA-F]{64}$").matches(candidate)) {
            setStatus(getString(R.string.vt_key_format_wrong), isError = true)
            return
        }
        saveButton.isEnabled = false
        setStatus(getString(R.string.vt_checking_key), isError = false)

        lifecycleScope.launch {
            val status = withContext(Dispatchers.IO) { VirusTotalClient.validateKey(candidate) }
            when (status) {
                VirusTotalClient.KeyStatus.VALID -> {
                    ApiKeyStore.save(this@ApiKeySetupActivity, candidate)
                    keyInput.setText("")            // do not leave the key on screen
                    setStatus(getString(R.string.vt_key_saved), isError = false)
                }
                VirusTotalClient.KeyStatus.RATE_LIMITED -> {
                    // The key was accepted but the free limit (4 requests per minute) is used up
                    ApiKeyStore.save(this@ApiKeySetupActivity, candidate)
                    keyInput.setText("")
                    setStatus(getString(R.string.vt_key_saved_rate_limited), isError = false)
                }
                VirusTotalClient.KeyStatus.INVALID ->
                    setStatus(getString(R.string.vt_key_invalid), isError = true)
                VirusTotalClient.KeyStatus.NETWORK_ERROR ->
                    setStatus(getString(R.string.vt_network_error), isError = true)
            }
            saveButton.isEnabled = true
            refreshSavedState()
        }
    }

    private fun removeKey() {
        ApiKeyStore.clear(this)
        setStatus(getString(R.string.vt_key_removed), isError = false)
        refreshSavedState()
    }

    private fun refreshSavedState() {
        val key = ApiKeyStore.get(this)
        savedKeyText.text = if (key == null) getString(R.string.vt_no_key_saved)
        else getString(R.string.vt_key_saved_as, ApiKeyStore.mask(key))
        testButton.isEnabled = key != null
    }

    private fun setStatus(message: String, isError: Boolean) {
        keyStatus.visibility = View.VISIBLE
        keyStatus.text = message
        keyStatus.setTextColor(
            getColor(if (isError) android.R.color.holo_red_dark else android.R.color.holo_green_dark)
        )
    }

    // ---- Part 3: testing ground ----------------------------------------------------------

    private fun runTest() {
        var url = testUrlInput.text.toString().trim()
        if (url.isEmpty()) return
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"

        testButton.isEnabled = false
        testResult.text = getString(R.string.vt_testing)

        lifecycleScope.launch {
            val started = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) {
                try { VirusTotalClient.checkUrlDetailed(this@ApiKeySetupActivity, url) }
                catch (e: Exception) { null }
            }
            val elapsed = System.currentTimeMillis() - started
            testResult.text = if (result == null) {
                getString(R.string.vt_test_failed)
            } else {
                getString(
                    R.string.vt_test_result,
                    result.verdict.name,
                    result.maliciousCount + result.suspiciousCount,
                    result.totalEnginesChecked,
                    elapsed
                )
            }
            testButton.isEnabled = ApiKeyStore.hasKey(this@ApiKeySetupActivity)
        }
    }
}