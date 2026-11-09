package com.darren.shieldlink

/** Overall safe/malicious verdict for a checked link. */
enum class LinkVerdict { SAFE, MALICIOUS, RESTRICTED, UNKNOWN }

/**
 * One antivirus engine's opinion on the URL (VirusTotal aggregates dozens
 * of these - e.g. Kaspersky, Sophos, Google Safebrowsing - each engine
 * scans independently and reports its own verdict).
 */
data class EngineResult(
    val engineName: String,   // e.g. "Kaspersky"
    val category: String,     // "malicious", "suspicious", "harmless", "undetected"
    val result: String        // e.g. "phishing", "malware", "clean site"
)

/** The full result of scanning one URL, built from VirusTotal's response. */
data class ScanResult(
    val verdict: LinkVerdict,
    val maliciousCount: Int,
    val suspiciousCount: Int,
    val totalEnginesChecked: Int,
    val flaggedEngines: List<EngineResult> // only the ones that flagged it
)