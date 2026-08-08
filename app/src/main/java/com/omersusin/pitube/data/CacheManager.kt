package com.omersusin.pitube.data

import android.content.Context
import android.util.Log
import androidx.media3.common.C
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.TreeSet

object CacheManager {
    private const val TAG = "CacheManager"
    private const val CACHE_DIR_NAME = "pitube_cache"
    const val DEFAULT_CACHE_SIZE_MB = 512L

    private var simpleCache: SimpleCache? = null
    private var databaseProvider: StandaloneDatabaseProvider? = null
    private var evictor: SizeAdjustableLruEvictor? = null
    private var maxCacheSizeBytes: Long = DEFAULT_CACHE_SIZE_MB * 1024 * 1024
    private val _currentCacheSizeBytes = MutableStateFlow(0L)
    val currentCacheSizeBytes: StateFlow<Long> = _currentCacheSizeBytes.asStateFlow()
    private var cacheDir: File? = null

    private class SizeAdjustableLruEvictor(initialMaxBytes: Long) : CacheEvictor {
        @Volatile private var maxBytes: Long = initialMaxBytes
        private val lock = Any()
        private val leastRecentlyUsed = TreeSet<CacheSpan>(Comparator { a, b ->
            val d = a.lastTouchTimestamp - b.lastTouchTimestamp
            if (d == 0L) a.compareTo(b) else if (d < 0) -1 else 1
        })
        private var currentSize = 0L
        override fun requiresCacheSpanTouches() = true
        override fun onCacheInitialized() {}
        override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
            if (length != C.LENGTH_UNSET.toLong()) evictWhileOverLimit(cache, length)
        }
        override fun onSpanAdded(cache: Cache, span: CacheSpan) {
            synchronized(lock) { leastRecentlyUsed.add(span); currentSize += span.length; _currentCacheSizeBytes.value = currentSize }
            evictWhileOverLimit(cache, 0)
        }
        override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
            synchronized(lock) { if (leastRecentlyUsed.remove(span)) { currentSize -= span.length; _currentCacheSizeBytes.value = currentSize } }
        }
        override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) { onSpanRemoved(cache, oldSpan); onSpanAdded(cache, newSpan) }
        fun updateMaxBytes(cache: Cache, newMaxBytes: Long) { maxBytes = newMaxBytes; evictWhileOverLimit(cache, 0) }
        private fun evictWhileOverLimit(cache: Cache, requiredSpace: Long) {
            while (true) {
                val toEvict = synchronized(lock) { if (currentSize + requiredSpace <= maxBytes) null else leastRecentlyUsed.firstOrNull() } ?: return
                cache.removeSpan(toEvict)
                synchronized(lock) { if (leastRecentlyUsed.remove(toEvict)) { currentSize -= toEvict.length; _currentCacheSizeBytes.value = currentSize } }
            }
        }
    }

    @Synchronized
    fun initialize(context: Context, maxSizeMb: Long = DEFAULT_CACHE_SIZE_MB) {
        if (simpleCache != null) { setMaxCacheSize(context, maxSizeMb); return }
        maxCacheSizeBytes = maxSizeMb * 1024 * 1024
        cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
        databaseProvider = StandaloneDatabaseProvider(context)
        try {
            evictor = SizeAdjustableLruEvictor(maxCacheSizeBytes)
            simpleCache = SimpleCache(cacheDir!!, evictor!!, databaseProvider!!)
        } catch (e: Exception) {
            try {
                cacheDir?.deleteRecursively(); cacheDir?.mkdirs()
                databaseProvider = StandaloneDatabaseProvider(context)
                evictor = SizeAdjustableLruEvictor(maxCacheSizeBytes)
                simpleCache = SimpleCache(cacheDir!!, evictor!!, databaseProvider!!)
            } catch (e2: Exception) { simpleCache = null; evictor = null }
        }
        updateCacheSize()
    }

    fun getCache(): SimpleCache? = simpleCache
    fun createCacheDataSourceFactory(context: Context): CacheDataSource.Factory? {
        val cache = simpleCache ?: return null
        return try {
            CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(DefaultDataSource.Factory(context)).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } catch (e: Exception) { null }
    }
    fun updateCacheSize() { simpleCache?.let { _currentCacheSizeBytes.value = it.cacheSpace } }
    @Synchronized fun clearCache() { try { simpleCache?.keys?.toList()?.forEach { simpleCache?.removeResource(it) }; updateCacheSize() } catch (e: Exception) {} }
    @Synchronized fun release() { try { simpleCache?.release(); simpleCache = null; databaseProvider = null; evictor = null } catch (e: Exception) {} }
    @Synchronized fun setMaxCacheSize(context: Context, maxSizeMb: Long) {
        val b = maxSizeMb * 1024 * 1024
        if (maxCacheSizeBytes == b) return
        maxCacheSizeBytes = b
        val c = simpleCache; val e = evictor
        if (c != null && e != null) { e.updateMaxBytes(c, b); updateCacheSize() }
    }
}
