package com.omersusin.pitube.data.local

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import com.omersusin.pitube.util.AppIcons
import com.omersusin.pitube.data.local.entity.PlaylistEntity
import com.omersusin.pitube.data.local.entity.PlaylistVideoCrossRef
import com.omersusin.pitube.data.local.entity.SubscriptionGroupEntity
import com.omersusin.pitube.data.local.entity.VideoEntity
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import java.io.StringReader
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class SettingsBackup(
    val strings: Map<String, String> = emptyMap(),
    val booleans: Map<String, Boolean> = emptyMap(),
    val ints: Map<String, Int> = emptyMap(),
    val floats: Map<String, Float> = emptyMap(),
    val longs: Map<String, Long> = emptyMap()
)

data class BackupData(
    val version: Int = 2,
    val timestamp: Long = System.currentTimeMillis(),
    val viewHistory: List<VideoHistoryEntry>? = emptyList(),
    val searchHistory: List<SearchHistoryItem>? = emptyList(),
    val subscriptions: List<ChannelSubscription>? = emptyList(),
    val playlists: List<PlaylistEntity>? = emptyList(),
    val playlistVideos: List<PlaylistVideoCrossRef>? = emptyList(),
    val videos: List<VideoEntity>? = emptyList(),
    val subscriptionGroups: List<SubscriptionGroupEntity>? = emptyList(),
    val likedVideos: List<LikedVideoInfo>? = emptyList(),
    val settings: SettingsBackup? = null
)

/**
 * Master-backup core: exports/imports the app snapshot used by the device-sync pre-merge rollback.
 */
class BackupRepository(private val context: Context) {
    private val playerPreferences = PlayerPreferences(context)
    private val localDataManager = LocalDataManager(context)
    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()
    private val viewHistory = ViewHistory.getInstance(context)
    private val searchHistoryRepo = SearchHistoryRepository(context)
    private val subscriptionRepo = SubscriptionRepository.getInstance(context)
    private val likedVideosRepo = LikedVideosRepository.getInstance(context)
    private val database = AppDatabase.getDatabase(context)

    private fun parseBackupJson(json: String): BackupData? {
        val reader = JsonReader(StringReader(json))
        reader.setStrictness(Strictness.LENIENT)
        return gson.fromJson(reader, BackupData::class.java)
    }

    private suspend fun getMergedSettingsBackup(): SettingsBackup {
        val playerSettings = playerPreferences.getExportData()
        val localSettings = localDataManager.getExportData()
        val searchSettings = searchHistoryRepo.getSettingsBackup()
        val activeIconSuffix = detectActiveIconSuffix()
        val exportedStrings = if (activeIconSuffix != null) {
            playerSettings.strings +
                mapOf("app_icon_suffix" to activeIconSuffix) +
                localSettings.strings +
                searchSettings.strings
        } else {
            playerSettings.strings + localSettings.strings + searchSettings.strings
        }
        return SettingsBackup(
            strings = exportedStrings,
            booleans = playerSettings.booleans + localSettings.booleans + searchSettings.booleans,
            ints = playerSettings.ints + localSettings.ints + searchSettings.ints,
            floats = playerSettings.floats + localSettings.floats + searchSettings.floats,
            longs = playerSettings.longs + localSettings.longs + searchSettings.longs
        )
    }

    /** Detect which launcher icon alias is currently enabled via PackageManager. */
    private fun detectActiveIconSuffix(): String? {
        val pm = context.packageManager
        val pkg = context.packageName
        return AppIcons.ALL_SUFFIXES.firstOrNull { suffix ->
            pm.getComponentEnabledSetting(
                ComponentName(pkg, "${AppIcons.NAMESPACE}$suffix")
            ) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
    }

    // ── Master Backup (app data in one ZIP) ──

    suspend fun exportMasterBackup(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val backupData = BackupData(
                viewHistory = viewHistory.getAllHistory().first(),
                searchHistory = searchHistoryRepo.getSearchHistoryFlow().first(),
                subscriptions = subscriptionRepo.getAllSubscriptions().first(),
                playlists = database.playlistDao().getAllPlaylistsUnscoped(),
                playlistVideos = database.playlistDao().getAllPlaylistVideoCrossRefs(),
                videos = database.videoDao().getAllVideos(),
                subscriptionGroups = database.subscriptionGroupDao().getAllGroupsOnce(),
                likedVideos = likedVideosRepo.getAllLikedVideos().first(),
                settings = getMergedSettingsBackup()
            )
            val appDataJson = gson.toJson(backupData)

            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                ZipOutputStream(out).use { zip ->
                    zip.putNextEntry(ZipEntry("app_data.json"))
                    zip.write(appDataJson.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            } ?: return@withContext Result.failure(Exception("Could not open output stream"))

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importMasterBackup(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            var appDataJson: String? = null

            context.contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(raw).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == "app_data.json") {
                            appDataJson = zip.readBytes().toString(Charsets.UTF_8)
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: return@withContext Result.failure(Exception("Could not read file"))

            val appData = appDataJson ?: return@withContext Result.failure(Exception("Invalid master backup file"))

            val backupData = parseBackupJson(appData)
                ?: return@withContext Result.failure(Exception("Invalid app data in backup"))
            importBackupData(backupData)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun importBackupData(backupData: BackupData) {
        backupData.viewHistory?.let { entries ->
            if (entries.isNotEmpty()) viewHistory.bulkSaveHistoryEntries(entries)
        }
        backupData.likedVideos?.forEach { info -> likedVideosRepo.likeVideo(info) }
        backupData.searchHistory?.let { searchHistoryRepo.replaceSearchHistory(it) }
        backupData.subscriptions?.let { subs ->
            subscriptionRepo.subscribeAll(subs)
        }
        database.withTransaction {
            backupData.videos?.forEach { database.videoDao().insertVideoOrIgnore(it) }
            backupData.playlists?.forEach { database.playlistDao().insertPlaylist(it) }
            backupData.playlistVideos?.forEach { database.playlistDao().insertPlaylistVideoCrossRef(it) }
            backupData.subscriptionGroups?.let { groups ->
                if (groups.isNotEmpty()) {
                    database.subscriptionGroupDao().insertAll(groups)
                }
            }
        }
        backupData.settings?.let { settings ->
            playerPreferences.restoreData(settings)
            localDataManager.restoreData(settings)
            searchHistoryRepo.restoreSettings(settings)
            val savedIconSuffix = settings.strings["app_icon_suffix"]
            if (!savedIconSuffix.isNullOrEmpty() && AppIcons.ALL_SUFFIXES.contains(savedIconSuffix)) {
                withContext(Dispatchers.Main) {
                    val pm = context.packageManager
                    val pkg = context.packageName
                    for (suffix in AppIcons.ALL_SUFFIXES) {
                        val cn = ComponentName(pkg, "${AppIcons.NAMESPACE}$suffix")
                        val want = if (suffix == savedIconSuffix)
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        else
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        pm.setComponentEnabledSetting(cn, want, PackageManager.DONT_KILL_APP)
                    }
                }
            }
        }
    }
}
