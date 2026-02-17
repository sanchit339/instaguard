package com.instaguard.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val latestTag: String,
    val releaseUrl: String,
    val isUpdateAvailable: Boolean
)

object GitHubUpdateChecker {
    private const val LATEST_RELEASE_ENDPOINT = "https://api.github.com/repos/sanchit339/instaguard/releases/latest"

    suspend fun check(currentVersionName: String): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(LATEST_RELEASE_ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "InstaGuard-Android")
            }

            connection.useConnection { conn ->
                if (conn.responseCode !in 200..299) {
                    error("Update check failed with HTTP ${conn.responseCode}")
                }
                val payload = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(payload)
                val tag = json.optString("tag_name").ifBlank { return@useConnection UpdateInfo("", "", false) }
                val url = json.optString("html_url")
                val available = VersionComparator.isRemoteNewer(remote = tag, local = currentVersionName)
                UpdateInfo(latestTag = tag, releaseUrl = url, isUpdateAvailable = available)
            }
        }
    }
}

private inline fun <T> HttpURLConnection.useConnection(block: (HttpURLConnection) -> T): T {
    return try {
        block(this)
    } finally {
        disconnect()
    }
}
