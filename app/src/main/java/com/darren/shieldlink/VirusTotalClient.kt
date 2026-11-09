package com.darren.shieldlink

import android.util.Base64
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Talks to the VirusTotal v3 API.
 * ⚠️ Put your real API key below - get one free at virustotal.com
 */
private fun getCachedResult(context: android.content.Context, url: String): ScanResult? {
    val prefs = context.getSharedPreferences("shieldlink_vt_cache", android.content.Context.MODE_PRIVATE)
    val cachedJson = prefs.getString(url, null) ?: return null
    return try {
        val obj = JSONObject(cachedJson)
        ScanResult(
            verdict = LinkVerdict.valueOf(obj.getString("verdict")),
            maliciousCount = obj.getInt("malicious"),
            suspiciousCount = obj.getInt("suspicious"),
            totalEnginesChecked = obj.getInt("total"),
            flaggedEngines = emptyList() // skip caching engine details, not worth the complexity
        )
    } catch (e: Exception) {
        null
    }
}

private fun cacheResult(context: android.content.Context, url: String, result: ScanResult) {
    if (result.verdict == LinkVerdict.UNKNOWN) return // don't cache inconclusive results
    val prefs = context.getSharedPreferences("shieldlink_vt_cache", android.content.Context.MODE_PRIVATE)
    val obj = JSONObject().apply {
        put("verdict", result.verdict.name)
        put("malicious", result.maliciousCount)
        put("suspicious", result.suspiciousCount)
        put("total", result.totalEnginesChecked)
    }
    prefs.edit().putString(url, obj.toString()).apply()
}
object VirusTotalClient {

    /** The user's own key, loaded from ApiKeyStore at the start of each check. */
    @Volatile private var apiKey: String = ""
    private const val BASE_URL = "https://www.virustotal.com/api/v3"
    private const val MALICIOUS_ENGINE_THRESHOLD = 2

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Does the full scan and returns a detailed ScanResult.
     * This does blocking network I/O - always call from Dispatchers.IO.
     */
    fun checkUrlDetailed(context: android.content.Context, url: String): ScanResult {

        getCachedResult(context, url)?.let { return it }
        // No key saved yet: return UNKNOWN (fail-open) so the app asks the user to add one
        apiKey = ApiKeyStore.get(context) ?: return emptyUnknownResult()
        val urlId = encodeUrlId(url)

        // Attributes that may contain last_http_response_content_sha256
        var hashSource: JSONObject? = fetchExistingReport(urlId)

        val urlResult: ScanResult = if (hashSource != null) {
            parseAttributesToScanResult(hashSource, sourceLabel = "URL")
        } else {
            val analysisId = submitUrlForScanning(url) ?: return emptyUnknownResult()
            var polled: ScanResult? = null
            for (attempt in 1..3) {
                Thread.sleep(1200)
                val body = fetchAnalysisResult(analysisId)
                if (body != null) {
                    polled = parseAttributesToScanResult(body, sourceLabel = "URL")
                    break                       // stop polling once completed
                }
            }
            if (polled == null) return emptyUnknownResult()
            // Analysis is done, so the URL report now exists: re-fetch it to get the file hash
            hashSource = fetchExistingReport(urlId)
            polled
        }

        val fileResult = hashSource?.let { checkFileHashIfPresent(it) } ?: return urlResult

        val combinedVerdict =
            if (urlResult.verdict == LinkVerdict.MALICIOUS || fileResult.verdict == LinkVerdict.MALICIOUS)
                LinkVerdict.MALICIOUS else urlResult.verdict

        return ScanResult(
            verdict = combinedVerdict,
            maliciousCount = urlResult.maliciousCount + fileResult.maliciousCount,
            suspiciousCount = urlResult.suspiciousCount + fileResult.suspiciousCount,
            totalEnginesChecked = maxOf(urlResult.totalEnginesChecked, fileResult.totalEnginesChecked),
            flaggedEngines = urlResult.flaggedEngines + fileResult.flaggedEngines
        )
    }

    /** Result of checking a key the user has just pasted. */
    enum class KeyStatus { VALID, INVALID, RATE_LIMITED, NETWORK_ERROR }

    /**
     * Checks a pasted key with one cheap lookup of a well-known URL.
     * Blocking network call: run on Dispatchers.IO. The key goes in a header, never in the URL.
     */
    fun validateKey(candidate: String): KeyStatus {
        val request = Request.Builder()
            .url("$BASE_URL/urls/${encodeUrlId("https://www.google.com")}")
            .addHeader("x-apikey", candidate.trim())
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200, 404 -> KeyStatus.VALID          // the key was accepted
                    401, 403 -> KeyStatus.INVALID        // wrong or disabled key
                    429 -> KeyStatus.RATE_LIMITED        // key looks fine, but the 4 per minute limit is used up
                    else -> KeyStatus.NETWORK_ERROR
                }
            }
        } catch (e: java.io.IOException) {
            KeyStatus.NETWORK_ERROR
        }
    }

    private fun encodeUrlId(url: String): String {
        return Base64.encodeToString(
            url.toByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    /** GET the existing report if VirusTotal already knows this URL. Returns the "attributes" JSON, or null. */
    private fun fetchExistingReport(urlId: String): JSONObject? {
        val request = Request.Builder()
            .url("$BASE_URL/urls/$urlId")
            .addHeader("x-apikey", apiKey)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 404 || !response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            return JSONObject(body).optJSONObject("data")?.optJSONObject("attributes")
        }
    }

    private fun submitUrlForScanning(url: String): String? {
        val formBody = FormBody.Builder().add("url", url).build()
        val request = Request.Builder()
            .url("$BASE_URL/urls")
            .addHeader("x-apikey", apiKey)
            .post(formBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            return JSONObject(body).optJSONObject("data")?.optString("id")
        }
    }

    /** Poll one analysis. Returns "attributes" JSON only once status == "completed", else null. */
    private fun fetchAnalysisResult(analysisId: String): JSONObject? {
        val request = Request.Builder()
            .url("$BASE_URL/analyses/$analysisId")
            .addHeader("x-apikey", apiKey)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val attributes = JSONObject(body).optJSONObject("data")?.optJSONObject("attributes")
                ?: return null
            return if (attributes.optString("status") == "completed") attributes else null
        }
    }

    /**
     * This is the key function for your "threat type" requirement:
     * it walks VirusTotal's per-engine "results" map and pulls out
     * every engine that flagged the link, with its category and label.
     */
    private fun parseAttributesToScanResult(attributes: JSONObject, sourceLabel: String = "URL"): ScanResult {
        // "stats" gives us the overall counts: malicious/suspicious/harmless/undetected
        val stats = attributes.optJSONObject("stats")
            ?: attributes.optJSONObject("last_analysis_stats")
            ?: JSONObject()

        val malicious = stats.optInt("malicious", 0)
        val suspicious = stats.optInt("suspicious", 0)
        val harmless = stats.optInt("harmless", 0)
        val undetected = stats.optInt("undetected", 0)
        val total = malicious + suspicious + harmless + undetected

        // "results" gives us the PER-ENGINE detail (which engine said what, and why)
        val resultsObj = attributes.optJSONObject("results")
            ?: attributes.optJSONObject("last_analysis_results")
            ?: JSONObject()

        val flagged = mutableListOf<EngineResult>()
        val engineNames = resultsObj.keys()
        while (engineNames.hasNext()) {
            val engineName = engineNames.next()
            val engineObj = resultsObj.optJSONObject(engineName) ?: continue
            val category = engineObj.optString("category", "undetected")
            if (category == "malicious" || category == "suspicious") {
                flagged.add(
                    EngineResult(
                        engineName = "[$sourceLabel] " + engineObj.optString("engine_name", engineName),
                        category = category,
                        result = engineObj.optString("result", category)
                    )
                )
            }
        }

        // Requiring more than one engine to agree filters out noise from a single
// unreliable/stale engine (the cause of shopee.com/dropbox.com/developer.android.com
// false positives in testing), while still catching genuine threats, which
// are almost always flagged by several engines at once, not just one.

        val verdict = if (malicious + suspicious >= MALICIOUS_ENGINE_THRESHOLD)
            LinkVerdict.MALICIOUS
        else
            LinkVerdict.SAFE

        return ScanResult(
            verdict = verdict,
            maliciousCount = malicious,
            suspiciousCount = suspicious,
            totalEnginesChecked = total,
            flaggedEngines = flagged
        )
    }

    private fun emptyUnknownResult() = ScanResult(
        verdict = LinkVerdict.UNKNOWN,
        maliciousCount = 0,
        suspiciousCount = 0,
        totalEnginesChecked = 0,
        flaggedEngines = emptyList()
    )
    /**
     * PHASE 3.5 — closes the URL-vs-file gap.
     * If VirusTotal's crawler already fetched a file behind this URL, check
     * that file's own malware verdict too, not just the URL's reputation.
     */
    private fun checkFileHashIfPresent(urlAttributes: JSONObject): ScanResult? {
        val fileHash = urlAttributes.optString("last_http_response_content_sha256", "")
        if (fileHash.isBlank()) return null // this URL didn't serve a raw file, nothing to check

        val request = Request.Builder()
            .url("$BASE_URL/files/$fileHash")
            .addHeader("x-apikey", apiKey)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val attributes = JSONObject(body).optJSONObject("data")?.optJSONObject("attributes")
                ?: return null
            // Re-use the same parser - file reports have the same stats/results shape as URL reports
            return parseAttributesToScanResult(attributes, sourceLabel = "File")
        }
    }
}