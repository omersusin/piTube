package com.omersusin.pitube.data

import android.content.Context
import android.util.Log
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.Cache
import androidx.media3.common.C
import androidx.media3.database.StandaloneDatabaseProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

object CacheManager {
    private const val TAG = "CacheManager"
    private const val CACHE_DIR_NAME = "pitube_cache"
    const val DEFAULT_CACHE_SIZE_MB = 512L
    const val MIN_CACHE_SIZE_MB = 128L
    const val MAX_CACHE_SIZE_MB = 4096L

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
        private val leastRecentlyUsed = TreeSet<CacheSpan>(::compareSpans)
        private var currentSize = 0L

        override fun requiresCacheSpanTouches(): Boolean = true
        override fun onCacheInitialized() {}

        override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
            if (length != C.LENGTH_UNSET.toLong()) {
                evictWhileOverLimit(cache, length)
            }
        }

        override fun onSpanAdded(cache: Cache, span: CacheSpan) {
            synchronized(lock) {
                leastRecentlyUsed.add(span)
                currentSize += span.length
                _currentCacheSizeBytes.value = currentSize
            }
            evictWhileOverLimit(cache, 0)
        }

        override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
            synchronized(lock) {
                if (leastRecentlyUsed.remove(span)) {
                    currentSize -= span.length
                    _currentCacheSizeBytes.value = currentSize
                }
            }
        }

        override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
            onSpanRemoved(cache, oldSpan)
            onSpanAdded(cache, newSpan)
        }

        fun updateMaxBytes(cache: Cache, newMaxBytes: Long) {
            maxBytes = newMaxBytes
            evictWhileOverLimit(cache, 0)
        }

        private fun evictWhileOverLimit(cache: Cache, requiredSpace: Long) {
            while (true) {
                val toEvict = synchronized(lock) {
                    if (currentSize + requiredSpace <= maxBytes) null
                    else leastRecentlyUsed.firstOrNull()
                } ?: return
                cache.removeSpan(toEvict)
                synchronized(lock) {
                    if (leastRecentlyUsed.remove(toEvict)) {
                        currentSize -= toEvict.length
                        _currentCacheSizeBytes.value = currentSize
                    }
                }
            }
        }

        private companion object {
            fun compareSpans(lhs: CacheSpan, rhs: CacheSpan): Int {
                val delta = lhs.lastTouchTimestamp - rhs.lastTouchTimestamp
                return when {
                    delta == 0L -> lhs.compareTo(rhs)
                    delta < 0L -> -1
                    else -> 1
                }
            }
        }
    }

    @Synchronized
    fun initialize(context: Context, maxSizeMb: Long = DEFAULT_CACHE_SIZE_MB) {
        if (simpleCache != null) {
            setMaxCacheSize(context, maxSizeMb)
            return
        }

        maxCacheSizeBytes = maxSizeMb * 1024 * 1024
        cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
        databaseProvider = StandaloneDatabaseProvider(context)

        try {
            evictor = SizeAdjustableLruEvictor(maxCacheSizeBytes)
            simpleCache = SimpleCache(cacheDir!!, evictor!!, databaseProvider!!)
            Log.d(TAG, "Cache initialized with max size: ${maxSizeMb}MB")
        } catch (e: Exception) {
            Log.e(TAG, "Cache initialization failed, attempting recovery", e)
            try {
                cacheDir?.deleteRecursively()
                cacheDir?.mkdirs()
                databaseProvider = StandaloneDatabaseProvider(context)
                evictor = SizeAdjustableLruEvictor(maxCacheSizeBytes)
                simpleCache = SimpleCache(cacheDir!!, evictor!!, databaseProvider!!)
                Log.d(TAG, "Cache recovery successful")
            } catch (e2: Exception) {
                Log.e(TAG, "Cache recovery failed - caching disabled", e2)
                simpleCache = null
                evictor = null
            }
        }
        updateCacheSize()
    }

    fun getCache(): SimpleCache? = simpleCache

    fun createCacheDataSourceFactory(context: Context): CacheDataSource.Factory? {
        val cache = simpleCache ?: return null
        try {
            val upstream = DefaultDataSource.Factory(context)
            return CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstream)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create cache data source factory", e)
            return null
        }
    }

    fun updateCacheSize() {
        simpleCache?.let { _currentCacheSizeBytes.value = it.cacheSpace }
    }

    @Synchronized
    fun clearCache() {
        try {
            simpleCache?.let { cache ->
                val keys = cache.keys.toList()
                keys.forEach { key -> cache.removeResource(key) }
                Log.d(TAG, "Cache cleared: removed ${keys.size} items")
            }
            updateCacheSize()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
        }
    }

    @Synchronized
    fun release() {
        try {
            simpleCache?.release()
            simpleCache = null
            databaseProvider = null
            evictor = null
            Log.d(TAG, "Cache released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing cache", e)
        }
    }

    @Synchronized
    fun setMaxCacheSize(context: Context, maxSizeMb: Long) {
        val newSizeBytes = maxSizeMb * 1024 * 1024
        if (maxCacheSizeBytes == newSizeBytes) return
        maxCacheSizeBytes = newSizeBytes

        val cache = simpleCache
        val currentEvictor = evictor
        if (cache == null || currentEvictor == null) return

        Log.d(TAG, "Updating cache size to ${maxSizeMb}MB (live)")
        currentEvictor.updateMaxBytes(cache, newSizeBytes)
        updateCacheSize()
    }
}
