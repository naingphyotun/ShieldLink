package com.darren.shieldlink

import android.content.Context
import java.net.URL

object GamblingFilter {

    private var cachedDomains: Set<String>? = null

    /**
     * Category-based content filter, separate from VirusTotal's
     * malware/phishing detection. Uses a curated domain list
     * (source: The Blocklist Project, github.com/blocklistproject/Lists)
     * since threat-intelligence APIs like VirusTotal do not classify
     * gambling content as malicious.
     */
    fun isGamblingDomain(context: Context, url: String): Boolean {
        val domains = loadDomains(context)
        val host = try {
            URL(url).host?.lowercase()?.removePrefix("www.")
        } catch (e: Exception) {
            null
        } ?: return false

        // Check exact match and parent-domain match (covers subdomains)
        return domains.any { blocked ->
            host == blocked || host.endsWith(".$blocked")
        }
    }

    private fun loadDomains(context: Context): Set<String> {
        cachedDomains?.let { return it }
        val domains = try {
            context.assets.open("gambling_domains.txt")
                .bufferedReader()
                .readLines()
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }
        cachedDomains = domains
        return domains
    }
}