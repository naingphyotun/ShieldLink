package com.darren.shieldlink

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Patterns
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LinkWatcherService : AccessibilityService() {

    // A service has no natural "lifecycle scope" like an Activity does,
    // so we make our own coroutine scope tied to this service's lifetime.
    private val serviceScope = CoroutineScope(Dispatchers.Default)

    // Avoid re-checking the exact same URL back-to-back (e.g. if the
    // same event fires twice for one tap).
    private var lastCheckedUrl: String? = null
    override fun onServiceConnected() {
        super.onServiceConnected()
        android.util.Log.d("ShieldLinkDebug", "Service CONNECTED and running")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        android.util.Log.d("ShieldLinkDebug", "Event fired: type=${event.eventType}, package=${event.packageName}")
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
            return
        }

        val source: AccessibilityNodeInfo = event.source ?: return
        val url = extractUrlFromNode(source)
        source.recycle() // always recycle node objects - they're a limited system resource

        if (url != null && url != lastCheckedUrl) {
            lastCheckedUrl = url
            checkUrlInBackground(url)
        }
    }

    /**
     * Walks the clicked node's text (and its direct children, since the
     * clickable area and the visible text aren't always the same node)
     * looking for anything URL-shaped.
     */
    private fun extractUrlFromNode(node: AccessibilityNodeInfo): String? {
        val text = node.text?.toString()
        if (text != null && Patterns.WEB_URL.matcher(text).find()) {
            return extractCleanUrl(text)
        }
        // Check immediate children too - many apps put the visible text
        // in a child TextView while the click event fires on a parent container.
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val childText = child.text?.toString()
            child.recycle()
            if (childText != null && Patterns.WEB_URL.matcher(childText).find()) {
                return extractCleanUrl(childText)
            }
        }
        return null
    }

    private fun extractCleanUrl(text: String): String? {
        val matcher = Patterns.WEB_URL.matcher(text)
        return if (matcher.find()) text.substring(matcher.start(), matcher.end()) else null
    }

    private fun checkUrlInBackground(url: String) {
        serviceScope.launch {
            // Check the gambling blocklist first - it's a local lookup,
            // no network call needed, so it's instant.
            if (GamblingFilter.isGamblingDomain(this@LinkWatcherService, url)) {
                showResult(url, LinkVerdict.RESTRICTED)
                return@launch
            }

            val result = try {
                VirusTotalClient.checkUrlDetailed(this@LinkWatcherService, url)
            } catch (e: Exception) {
                return@launch
            }

            showResult(url, result.verdict)
        }
    }

    private fun showResult(url: String, verdict: LinkVerdict) {
        val targetActivity = when (verdict) {
            LinkVerdict.MALICIOUS -> WarningActivity::class.java
            LinkVerdict.RESTRICTED -> WarningActivity::class.java
            LinkVerdict.SAFE -> SafeActivity::class.java       // ← temporary, for testing
            LinkVerdict.UNKNOWN -> return
        }

        val intent = Intent(this@LinkWatcherService, targetActivity).apply {
            putExtra(WarningActivity.EXTRA_URL, url)
            putExtra(WarningActivity.EXTRA_CATEGORY, verdict.name)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        // Required override - called if the system needs to stop the service abruptly
    }
}