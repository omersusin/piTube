# Koda Backend Port — muse Branch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port Koda's entire auth/session + feed backend to piTube so content appears instantly after Google login.

**Architecture:** Copy Koda's pure-JVM `YouTubeAuthUtils` (SAPISIDHASH), `SessionCookieJar` write-only, `ProfileManager` per-profile `cookies_<id>` EncryptedSharedPreferences, `SessionManager` + `AccountSwitcher` invalidation, and `YouTubeRepository` visitorData 6h TTL + origin-bound header building (`WEB`→www / `WEB_REMIX`→music) into piTube's `innertube/InnerTube.kt` + `data/local/*`. Feed parity: `FEwhat_to_watch(WEB)` → `FEmusic_home(WEB_REMIX)` → taste-lane history-seeded (`FEED_CONCURRENCY=6`). Keep piTube's SABR/PoToken, Coil 3.5, Paging3 intact.

**Tech Stack:** Kotlin, Ktor OkHttp, InnerTube WEB(1)/WEB_REMIX(67)/ANDROID_VR(28)/IOS(5), EncryptedSharedPreferences, Room, Coil 3.5, Hilt

**Spec:** `MASTER_PLAN_TUR5.md` §2 + §4 Faz C (C1 account sync, C2 Shorts, C3 WL-sort, C4 feed health, C5 scrub)

## Global Constraints
- minSdk 26, targetSdk 36, compileSdk 37
- versionCode/versionName bump every push (semantic)
- Single push discipline: all work in one push, CI poll max 3 min sleep
- Keep Paging3, Coil 3.5, SABR/PoToken, download stack — no downgrade
- Keep `SessionManager.restored` 1.5s cold-start gate
- Never send `datasyncId` as `user.onBehalfOfUser` — 401 bot-wall

---

### Task 1: Tur-2 Dump Instrumentation + FEtrending Helper

**Files:**
- Modify: `app/src/main/java/com/omersusin/pitube/innertube/YouTube.kt:798-860`
- Modify: `app/src/main/java/com/omersusin/pitube/ui/screens/home/HomeViewModel.kt:788-834`
- Modify: `app/src/main/java/com/omersusin/pitube/innertube/InnerTube.kt`

**Interfaces:**
- Consumes: `InnerTube.signedWebBrowse`, `YouTube.personalizedFeedPage`
- Produces: `InnerTube.trendingBrowse(): Result<BrowseResponse>` for Task 6

- [ ] **Step 1: Add EMPTY dump in YouTube.personalizedFeedPage**

```kotlin
if (parsed.videos.isEmpty() && parsed.continuation == null) {
    val marker = when {
        body.contains("signin") || body.contains("LOGIN_REQUIRED") -> "login-required banner"
        body.contains("consistency") || body.contains("botguard") -> "bot-guard interstitial"
        body.length < 5000 -> "suspiciously tiny body len=${body.length}"
        else -> "parsed-empty"
    }
    Log.w("YouTube", "personalizedFeed($browseId): EMPTY — $marker bodyLen=${body.length} head=${body.take(2000)}")
}
```

- [ ] **Step 2: Verify HomeViewModel logs exist (already at 802/812/832) — add missing `Log.w` if absent**
- [ ] **Step 3: Add InnerTube.trendingBrowse helper**

```kotlin
suspend fun trendingBrowse(): Result<BrowseResponse> =
    signedWebBrowse(client = YouTubeClient.WEB, browseId = "FEtrending")
```

- [ ] **Step 4: Build check** `gradle :app:assembleDebug` must pass
- [ ] **Step 5: Commit** `feat(muse): add personalizedFeed EMPTY dump + FEtrending helper`

---

### Task 2: DISCOVERY_QUERIES Taste-Lane Diversification

**Files:**
- Modify: `app/src/main/java/com/omersusin/pitube/ui/screens/home/HomeViewModel.kt:281-391`

**Interfaces:**
- Consumes: `ViewHistory.getLatestUnfinishedVideo()`, `YouTubeRepository.getRelatedVideos`
- Produces: diversified `discoveryQueries` list

- [ ] **Step 1: Expand DISCOVERY_QUERIES pool (50 → ~80) with Koda VIDEO_EXPLORE_TOPICS parity + history-seeded seeds**
- [ ] **Step 2: Keep tastePool 4-seed `getRelatedVideos` interleaving + seedOffset rotation on DISCOVERY_ROTATE_MS=12h**
- [ ] **Step 3: Wave1 deferredDiscovery 2 queries shuffled per epoch, Wave2 drop(2) preserved**
- [ ] **Step 4: Manual verify signed-out Keşfet shows interleaved variety**
- [ ] **Step 5: Commit** `feat(muse): diversify taste-lane discovery queries`

---

### Task 3: Koda Auth Core — YouTubeAuthUtils + CookieRotation

**Files:**
- Modify: `app/src/main/java/com/omersusin/pitube/data/local/YouTubeAuthUtils.kt`
- Modify: `app/src/main/java/com/omersusin/pitube/data/local/CookieRotation.kt`
- Test: `app/src/test/java/com/omersusin/pitube/data/local/YouTubeAuthUtilsTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
@Test fun `SAPISIDHASH origin-bound differs for music vs www`() {
    val cookie = "SAPISID=abc123; SID=xyz;"
    val h1 = YouTubeAuthUtils.getAuthorizationHeader(cookie, "https://music.youtube.com")
    val h2 = YouTubeAuthUtils.getAuthorizationHeader(cookie, "https://www.youtube.com")
    assertNotEquals(h1, h2)
}
```

- [ ] **Step 2: Run test — expect FAIL (if not origin-bound)**
- [ ] **Step 3: Align YouTubeAuthUtils to Koda 1:1**
  - `SAPISID_NAMES = [SAPISID, __Secure-3PAPISID]`
  - `getSapisid`, `getCookieValue(limit=2)`, `normalizeCookieString`, `missingRequiredCookies(SAPISID+SID)`, `getAuthorizationHeader(origin)` → `SHA1("$ts $sapisid $origin")`
- [ ] **Step 4: Align CookieRotation REFRESHABLE 15 names + PSIDTS pair guard + LinkedHashMap merge**
- [ ] **Step 5: Test passes**
- [ ] **Step 6: Commit** `feat(muse): align YouTubeAuthUtils + CookieRotation to Koda`

---

### Task 4: SessionCookieJar + InnerTube.ytClient Origin-Bound Signing

**Files:**
- Modify: `app/src/main/java/com/omersusin/pitube/innertube/InnerTube.kt:177-356`

- [ ] **Step 1: Assert SessionCookieJar.loadForRequest returns emptyList (write-only invariant)**
- [ ] **Step 2: Fix saveFromResponse — only youtube.com/google.com, expiresAt>now, REFRESHABLE whitelist**
- [ ] **Step 3: Fix ytClient(setLogin) — Cookie + X-Goog-AuthUser:0 + SAPISIDHASH(origin) where origin = apiUrl host**
- [ ] **Step 4: X-Goog-Visitor-Id only on /player, not browse/next**
- [ ] **Step 5: Commit** `feat(muse): fix SessionCookieJar + ytClient origin-bound signing`

---

### Task 5: Session Persistence + Account Switch Invalidation

**Files:**
- Modify: `app/src/main/java/com/omersusin/pitube/data/local/ProfileManager.kt`
- Modify: `app/src/main/java/com/omersusin/pitube/data/local/SessionManager.kt`
- Modify: `app/src/main/java/com/omersusin/pitube/data/local/AccountSwitcher.kt`
- Modify: `app/src/main/java/com/omersusin/pitube/FlowApplication.kt:155-294`

- [ ] **Step 1: Diff ProfileManager yt_music_session cookies_<id> + active_profile + datasyncId dedupe vs Koda 122-148**
- [ ] **Step 2: Fix AccountSwitcher.switchTo → setActive + invalidateForProfileChange (visitorData clear + HomeFeedCache + Room + flow_prefs) offline, no network**
- [ ] **Step 3: Fix FlowApplication restored gate 1.5s withTimeout + visitorData 6h TTL remint on LOGIN_REQUIRED**
- [ ] **Step 4: Guard: never add datasyncId to request Context.user**
- [ ] **Step 5: Commit** `feat(muse): align session persistence + account switch invalidation`

---

### Task 6: Feed Personalization Chain Parity

**Files:**
- Modify: `app/src/main/java/com/omersusin/pitube/innertube/YouTube.kt:798-860`
- Modify: `app/src/main/java/com/omersusin/pitube/ui/screens/home/HomeViewModel.kt:753-1222`

- [ ] **Step 1: personalizedFeed FEwhat_to_watch WEB/www signed → empty ? musicHomeFeed FEmusic_home WEB_REMIX/music signed**
- [ ] **Step 2: parseVideosFromYouTubeJson dual path richGridRenderer(lockupViewModel) + sectionListRenderer, distinctBy take(30)**
- [ ] **Step 3: extractRichGridContinuation + getVideoFeedContinuation appendContinuationItemsAction**
- [ ] **Step 4: Use filterSignedValid (drop shorts only) for personal/subs lanes — no 120s duration filter**
- [ ] **Step 5: Commit** `feat(muse): align feed personalization chain to Koda`

---

### Task 7: Subscriptions Scale — RSS Fast Path

**Files:**
- Modify: `app/src/main/java/com/omersusin/pitube/data/repository/YouTubeRepository.kt` or `data/local/SubscriptionRepository.kt`
- Modify: `app/src/main/java/com/omersusin/pitube/innertube/YouTube.kt:1594-1687`

- [ ] **Step 1: Add getLocalSubscriptionsFeed fast path GET feeds/videos.xml?channel_id=UC… XmlPullParser namespace-unaware, FEED_CONCURRENCY=6, MAX_PER_CHANNEL=15, MAX_TOTAL=300**
- [ ] **Step 2: FEchannels paginated channelRenderer 10 pages cap distinctBy**
- [ ] **Step 3: Replace parseRelativeTime prose with publishedAtMs RSS published for merge sort**
- [ ] **Step 4: Commit** `feat(muse): add RSS fast path for subscriptions feed`

---

### Task 8: Verification + Bump + Push

**Files:**
- Modify: `app/build.gradle.kts` (version bump)
- Modify: `MASTER_PLAN_TUR5.md` (tracking entry)

- [ ] **Step 1: Run** `./gradlew :app:assembleDebug` passes
- [ ] **Step 2: Bump versionCode/versionName (minor: new feature)**
- [ ] **Step 3: Update MASTER_PLAN_TUR5.md + cp to sdcard**
- [ ] **Step 4: Push to muse branch, watch CI, then ask verification questions (personal feed populated? trending fallback? Keşfet varied? 2216 subs no OOM?)**
