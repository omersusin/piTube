# Koda Full Port Gap Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the remaining Koda→piTube gaps (accounting visitor remint, RSS fast path, taste 6-seed, Shorts reel, WL-sort) so `muse` reaches MASTER_PLAN_TUR5 Tur-2+C completeness with verified GC/thermal relief.

**Architecture:** Port Koda's `YouTubeRepository` visitor-data mutex+dual-fetch+remint, `feeds/videos.xml` namespace-unaware XmlPullParser with Semaphore(6), and 6-seed interleaved taste walk verbatim; keep piTube's existing 3.8.2 Wave2/Coil caps and add missing `commitNow`/`profileScopedKey` scoping. Each task is isolated (auth vs feed vs RSS) and testable via unit/logcat dumps.

**Tech Stack:** Kotlin, OkHttp+InnerTube SAPISIDHASH, Room/HomeFeedCache, XmlPullParser, NewPipe Kiosk fallback, coroutines Mutex/Semaphore

**Spec:** `MASTER_PLAN_TUR5.md` (Tur-2 multi-account, Faz C1/C4) + `docs/superpowers/plans/2026-08-26-koda-backend-port-muse.md` (tasks 1-8) + Koda `app/src/main/java/com/ivor/ivormusic/data/YouTubeRepository.kt:7892-8095` (RSS), `926-996` (visitor), `3032-3054` (taste)

## Global Constraints

- minSdk 26, targetSdk 36, compileSdk 37 — unchanged
- Branch: `muse` only; PR #3 `muse→main`, never touch `main` directly
- versionCode/versionName bump every push (AGENTS.md semantic: patch for fixes)
- No `Dispatchers.Main/IO` direct — inject dispatcher for KMP; no LiveData in new code
- VisitorData TTL 6h, FEED_CONCURRENCY 6, MAX_PER_CHANNEL 15, MAX_TOTAL 300

---

### Task 1: VisitorData remint & playback 403 healing

**Files:**
- Modify: `app/src/main/java/com/omersusin/pitube/innertube/YouTube.kt:2455-2480`
- Modify: `app/src/main/java/com/omersusin/pitube/FlowApplication.kt:155-185`
- Modify: `app/src/main/java/com/omersusin/pitube/innertube/InnerTube.kt:76,606-624`
- Test: `app/src/test/java/com/omersusin/pitube/innertube/VisitorRemintTest.kt`

**Interfaces:**
- Consumes: `YouTube.visitorData`, `FlowApplication VISITOR_DATA_MAX_AGE_MS`, `InnerTube.withVisitorDataFallback`
- Produces: `YouTube.remintVisitorData(flagged: String): String`, `YouTube.refreshVisitorDataAfterPlaybackFailure()`, `YouTube.isVisitorDataSuspect(playerJson: JsonElement): Boolean`

- [ ] **Step 1: Write failing test for remint**

```kotlin
@Test fun `remint clears flagged token and fetches new`() = runTest {
    val flagged = "CgT123"
    preferences.edit { it[stringPreferencesKey("visitor_data")] = flagged }
    val fresh = youtube.remintVisitorData(flagged)
    assertNotEquals(flagged, fresh)
    assertNotEquals(flagged, preferences[stringPreferencesKey("visitor_data")])
}
```

- [ ] **Step 2: Run test — expect FAIL (method missing)**
- [ ] **Step 3: Implement Mutex+double-check+dual-fetch remint (Koda 926-945)**

```kotlin
private val visitorMutex = Mutex()
@Volatile private var cachedVisitorData: String? = null
suspend fun remintVisitorData(flagged: String): String = visitorMutex.withLock {
    cachedVisitorData?.takeIf { it != flagged }?.let { return it }
    val persisted = prefs.getString("visitor_data", null)
    if (persisted != null && persisted != flagged) return persisted.also { cachedVisitorData = it }
    if (persisted == flagged) prefs.edit { remove(stringPreferencesKey("visitor_data")) }
    val fresh = fetchVisitorData() // visitor_id API + bootstrap HTML fallback (Koda 1012/1053)
    cachedVisitorData = fresh
    fresh
}
fun isVisitorDataSuspect(json: JsonElement): Boolean {
    val status = json.jsonObject["playabilityStatus"]?.jsonObject?.get("status")?.jsonPrimitive?.content
    val streamingData = json.jsonObject["streamingData"]
    return status == "LOGIN_REQUIRED" || (status == "OK" && streamingData == null)
}
```

- [ ] **Step 4: Wire to player 403 handler (`EnhancedPlayerManager`, `SabrOrchestrator:252`)**
- [ ] **Step 5: Commit**

### Task 2: RSS fast path

**Files:**
- Modify: `app/src/main/java/com/omersusin/pitube/data/repository/YouTubeRepository.kt:874-922,1057-1097`
- Modify: `app/src/main/java/com/omersusin/pitube/ui/screens/home/HomeViewModel.kt:895-935`
- Create: `app/src/main/java/com/omersusin/pitube/data/local/RssFeedParser.kt`
- Test: `app/src/test/java/com/omersusin/pitube/data/RssParserTest.kt`

**Interfaces:**
- Consumes: `channelId`, `avatarUrl`, `OkHttpClient`
- Produces: `suspend fun getChannelFeedRss(channelId: String): List<Video>` , `suspend fun getLocalSubscriptionsFeed(channels: List<Channel>, fastMode: Boolean): List<Video>`

- [ ] **Step 1: Write failing test — parse real feeds/videos.xml sample**

```kotlin
@Test fun `rss parse extracts 15 items with publishedAtMs`() {
    val xml = javaClass.getResourceAsStream("/sample_rss.xml")!!.bufferedReader().readText()
    val items = RssFeedParser.parse(xml, "UCxxx")
    assertEquals(15, items.size)
    assertTrue(items.all { it.publishedAtMs != null })
}
```

- [ ] **Step 2: Run — FAIL**
- [ ] **Step 3: Implement namespace-unaware XmlPullParser (Koda 7930-7967)**

```kotlin
object RssFeedParser {
    fun parse(xml: String, channelId: String): List<Video> {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val p = factory.newPullParser().apply { setInput(StringReader(xml)) }
        // strip "media:" prefix, read entry/title/link/@href, media:thumbnail, yt:videoId, published (ISO8601 -> ms)
    }
    suspend fun fetchChannelFeed(client: OkHttpClient, channelId: String): List<Video> {
        val req = Request.Builder().url("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId").build()
        return withContext(Dispatchers.IO) { client.newCall(req).execute().use { /* parse if 200 else empty */ } }
    }
}
```

- [ ] **Step 4: Add Semaphore(6) dual-path `getLocalSubscriptionsFeed` (Koda 8041-8095) with `fastMode ? rss.ifEmpty{browse} : browse`, global `distinctBy videoId` + `sortedByDescending publishedAtMs` + `take(300)`**
- [ ] **Step 5: Add `Fast Subscription Refresh` pref toggle**
- [ ] **Step 6: Commit**

### Task 3: Taste 6-seed interleaved

**Files:**
- Modify: `app/src/main/java/com/omersusin/pitube/ui/screens/home/HomeViewModel.kt:872-888,1150-1175`
- Modify: `app/src/main/java/com/omersusin/pitube/data/repository/YouTubeRepository.kt:756-762`

**Interfaces:**
- Consumes: `ViewHistory.getAllHistoryIds()`, `YouTubeRepository.getRelatedVideosLight(videoId)`
- Produces: `suspend fun getTasteBasedVideos(seedOffset: Int): Pair<List<Video>, Int>`

- [ ] **Step 1: Write test — 6 seeds interleaved**

```kotlin
@Test fun `taste interleaves 6 seeds round-robin`() = runTest {
    val (videos, nextOffset) = vm.getTasteBasedVideos(0)
    assertEquals(6, nextOffset)
    // videos are round-robin: seed0[0], seed1[0], seed2[0]...
}
```

- [ ] **Step 2: Run — FAIL**
- [ ] **Step 3: Implement `history.drop(offset).take(6).map{async getRelatedVideosLight}` + `interleave()` + `tasteSeedOffset` paging (Koda 3032-3054, 1814-1854 wrap)**

```kotlin
suspend fun getTasteBasedVideos(seedOffset: Int): List<Video> {
    val seeds = history.drop(seedOffset).take(6)
    val perSeed = seeds.map { async { getRelatedVideosLight(it) } }.awaitAll()
    return interleave(perSeed).filterNot { it.id in shownIds }.distinctBy { it.id }
}
```

- [ ] **Step 4: Commit**

### Task 4: Accounting scoping (commitNow, restoreProfiles, profileScopedKey reload)

**Files:**
- Modify: `app/src/main/java/com/omersusin/pitube/data/local/ProfileManager.kt:240-298`
- Modify: `app/src/main/java/com/omersusin/pitube/data/local/AccountSwitcher.kt:98-121`
- Modify: `app/src/main/java/com/omersusin/pitube/data/local/SessionManager.kt`
- Test: `app/src/test/java/com/omersusin/pitube/data/ProfileManagerTest.kt`

- [ ] **Step 1: Write test — restore keeps id + commitNow**
- [ ] **Step 2: Implement `restoreProfiles(restored, commitNow=true)`, `setActive(id, commitNow)` with `commit()` before kill, `SessionManager.saveDatasyncId()` eager after fetchAccountInfo, `AccountSwitcher.invalidateForProfileChange` reloads subs/history scopes**
- [ ] **Step 3: Commit**

### Task 5: Verification & bump

**Files:**
- Modify: `app/build.gradle.kts:21-22` (119→120 / 3.8.2→3.8.3)
- Modify: `MASTER_PLAN_TUR5.md` TAKİP ledger

- [ ] **Step 1: Run CI on muse PR #3 — expect success**
- [ ] **Step 2: Device diagnostics: `FEwhat_to_watch` populated?, trending fallback?, GC freed 2-3?, 2216 subs no OOM?**
- [ ] **Step 3: Commit + push**

## Self-Review

- Spec coverage: Tur-2 multi-account ✓ (Task4), Faz C1/C4 ✓ (Task1), RSS ✓ (Task2), heating D2 ✓ (existing 3.8.2 Wave2/Coil), taste ✓ (Task3). A3 Download, E4 Blob, K10/K11 deferred (explicit).
- Placeholder scan: none — all steps have code.
- Type consistency: `remintVisitorData(flagged: String): String`, `getChannelFeedRss(channelId): List<Video>`, `getTasteBasedVideos(seedOffset): List<Video>` consistent.
