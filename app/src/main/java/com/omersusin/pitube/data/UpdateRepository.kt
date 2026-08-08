package com.omersusin.pitube.data

import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class UpdateRepository {
    private val TAG = "UpdateRepository"
    private val GITHUB_API_BASE = "https://api.github.com/repos"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun checkForUpdate(repoPath: String, currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val url = "$GITHUB_API_BASE/$repoPath/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "piTube")
                .build()
            val response = client.newCall(request).execute()
            when (response.code) {
                200 -> {
                    val json = response.body?.string() ?: return@withContext UpdateResult.Error("Empty response")
                    val jsonObject = JSONObject(json)
                    val tagName = jsonObject.optString("tag_name", "")
                    val releaseName = jsonObject.optString("name", tagName)
                    val releaseNotes = jsonObject.optString("body", "")
                    val htmlUrl = jsonObject.optString("html_url", "")
                    val publishedAt = jsonObject.optString("published_at", "")
                    val apkAssets = mutableListOf<ApkAsset>()
                    val assets = jsonObject.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk")) {
                                apkAssets.add(ApkAsset(name, asset.optString("browser_download_url"), asset.optLong("size", 0L)))
                            }
                        }
                    }
                    val latestVersion = tagName.removePrefix("v").removePrefix("V")
                    val cleanCurrent = currentVersion.removePrefix("v").removePrefix("V")
                    val isUpdate = isNewerVersion(latestVersion, cleanCurrent)
                    if (isUpdate) {
                        UpdateResult.UpdateAvailable(
                            latestVersion = latestVersion,
                            releaseName = releaseName,
                            releaseNotes = releaseNotes,
                            htmlUrl = htmlUrl,
                            apkAssets = apkAssets,
                            apkDownloadUrl = findBestApk(apkAssets)?.downloadUrl,
                            publishedAt = publishedAt,
                            releaseImages = parseImagesFromMarkdown(releaseNotes)
                        )
                    } else UpdateResult.UpToDate(currentVersion = cleanCurrent)
                }
                404 -> UpdateResult.NoReleases
                else -> UpdateResult.Error("GitHub API error (${response.code})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            UpdateResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        try {
            val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            val maxLength = maxOf(latestParts.size, currentParts.size)
            val pL = latestParts + List(maxLength - latestParts.size) { 0 }
            val pC = currentParts + List(maxLength - currentParts.size) { 0 }
            for (i in 0 until maxLength) {
                if (pL[i] > pC[i]) return true
                if (pL[i] < pC[i]) return false
            }
            return false
        } catch (e: Exception) { return false }
    }

    private fun parseImagesFromMarkdown(markdown: String): List<String> {
        val images = mutableListOf<String>()
        Regex("""!\[.*?]\((.*?)\)""").findAll(markdown).forEach { match ->
            match.groupValues.getOrNull(1)?.let { url ->
                if (url.startsWith("http") && url !in images) images.add(url)
            }
        }
        Regex("""(https://(?:user-images\.githubusercontent\.com|github\.com)[^\s)\"]+\.(?:png|jpg|jpeg|gif|webp))""", RegexOption.IGNORE_CASE)
            .findAll(markdown).forEach { match ->
                val url = match.groupValues[0]
                if (url !in images) images.add(url)
            }
        return images
    }

    companion object {
        fun getDeviceAbi(): String = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        fun findBestApk(assets: List<ApkAsset>): ApkAsset? {
            val abi = getDeviceAbi()
            assets.find { it.name.contains(abi, ignoreCase = true) }?.let { return it }
            val simplified = when {
                abi.contains("arm64") || abi.contains("v8a") -> assets.find {
                    it.name.contains("v8a", ignoreCase = true) || it.name.contains("arm64", ignoreCase = true)
                }
                abi.contains("armeabi") || abi.contains("v7a") -> assets.find {
                    it.name.contains("v7a", ignoreCase = true) || it.name.contains("armeabi", ignoreCase = true)
                }
                else -> null
            }
            if (simplified != null) return simplified
            return assets.find { it.name.contains("universal", ignoreCase = true) } ?: assets.firstOrNull()
        }
    }
}

data class ApkAsset(val name: String, val downloadUrl: String, val size: Long)

sealed class UpdateResult {
    data class UpdateAvailable(
        val latestVersion: String,
        val releaseName: String,
        val releaseNotes: String,
        val htmlUrl: String,
        val apkAssets: List<ApkAsset> = emptyList(),
        val apkDownloadUrl: String?,
        val publishedAt: String,
        val releaseImages: List<String> = emptyList()
    ) : UpdateResult()
    data class UpToDate(val currentVersion: String) : UpdateResult()
    object NoReleases : UpdateResult()
    data class Error(val message: String) : UpdateResult()
    object Checking : UpdateResult()
}
