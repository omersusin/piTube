package com.omersusin.pitube.data
import android.content.Context
import androidx.media3.common.C
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.TreeSet
object CacheManager {
    const val DEFAULT_CACHE_SIZE_MB = 512L
    private var simpleCache: SimpleCache? = null
    private var databaseProvider: StandaloneDatabaseProvider? = null
    private var evictor: SizeAdjustableLruEvictor? = null
    private var maxBytes: Long = DEFAULT_CACHE_SIZE_MB * 1024 * 1024
    private val _size = MutableStateFlow(0L); val currentCacheSizeBytes: StateFlow<Long> = _size.asStateFlow()
    private var cacheDir: File? = null
    private class SizeAdjustableLruEvictor(initial: Long) : CacheEvictor {
        @Volatile private var max: Long = initial
        private val lock = Any()
        private val lru = TreeSet<CacheSpan>(Comparator { a, b -> val d = a.lastTouchTimestamp - b.lastTouchTimestamp; if (d == 0L) a.compareTo(b) else if (d < 0) -1 else 1 })
        private var size = 0L
        override fun requiresCacheSpanTouches() = true
        override fun onCacheInitialized() {}
        override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) { if (length != C.LENGTH_UNSET.toLong()) evict(cache, length) }
        override fun onSpanAdded(cache: Cache, span: CacheSpan) { synchronized(lock) { lru.add(span); size += span.length; _size.value = size }; evict(cache, 0) }
        override fun onSpanRemoved(cache: Cache, span: CacheSpan) { synchronized(lock) { if (lru.remove(span)) { size = (size - span.length).coerceAtLeast(0); _size.value = size } } }
        override fun onSpanTouched(cache: Cache, old: CacheSpan, new: CacheSpan) { onSpanRemoved(cache, old); onSpanAdded(cache, new) }
        fun updateMax(cache: Cache, m: Long) { max = m; evict(cache, 0) }
        private fun evict(cache: Cache, req: Long) { while (true) { val v = synchronized(lock) { if (size + req <= max) null else lru.firstOrNull() } ?: return; cache.removeSpan(v); synchronized(lock) { if (lru.remove(v)) { size = (size - v.length).coerceAtLeast(0); _size.value = size } } } }
    }
    @Synchronized fun initialize(context: Context, mb: Long = DEFAULT_CACHE_SIZE_MB) {
        if (simpleCache != null) { setMaxCacheSize(context, mb); return }
        maxBytes = mb * 1024 * 1024; cacheDir = File(context.cacheDir, "pitube_cache"); databaseProvider = StandaloneDatabaseProvider(context)
        try { evictor = SizeAdjustableLruEvictor(maxBytes); simpleCache = SimpleCache(cacheDir!!, evictor!!, databaseProvider!!) }
        catch (e: Exception) { try { cacheDir?.deleteRecursively(); cacheDir?.mkdirs(); databaseProvider = StandaloneDatabaseProvider(context); evictor = SizeAdjustableLruEvictor(maxBytes); simpleCache = SimpleCache(cacheDir!!, evictor!!, databaseProvider!!) } catch (e2: Exception) { simpleCache = null; evictor = null } }
        update()
    }
    fun getCache(): SimpleCache? = simpleCache
    fun createCacheDataSourceFactory(context: Context): CacheDataSource.Factory? { val c = simpleCache ?: return null; return try { CacheDataSource.Factory().setCache(c).setUpstreamDataSourceFactory(DefaultDataSource.Factory(context)).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR) } catch (e: Exception) { null } }
    private fun update() { simpleCache?.let { _size.value = it.cacheSpace } }
    @Synchronized fun clearCache() { try { simpleCache?.keys?.toList()?.forEach { simpleCache?.removeResource(it) }; update() } catch (e: Exception) {} }
    @Synchronized fun release() { try { simpleCache?.release(); simpleCache = null; databaseProvider = null; evictor = null } catch (e: Exception) {} }
    @Synchronized fun setMaxCacheSize(context: Context, mb: Long) { val b = mb * 1024 * 1024; if (maxBytes == b) return; maxBytes = b; val c = simpleCache; val e = evictor; if (c != null && e != null) { e.updateMax(c, b); update() } }
}
