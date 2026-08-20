<p align="center">
  <img src="https://img.shields.io/badge/DEVELOPMENT%20STOPPED-Will%20be%20continued%20as%20soon%20as%20possible-dc143c?style=for-the-badge" alt="Development stopped — will be continued as soon as possible">
</p>

# piTube

> **The privacy-first YouTube client for Android — rebuilt from the Flow codebase with high changes, powered by the NewPipeExtractor pipeline and the free-software YouTube ecosystem.**

<p align="center">
  <img src="https://img.shields.io/badge/version-2.2.0-e53935?style=flat-square" alt="Version 2.2.0">
  <img src="https://img.shields.io/badge/license-GPL--3.0-e53935?style=flat-square" alt="License GPL-3.0">
  <img src="https://img.shields.io/badge/platform-Android%208.0%2B-e53935?style=flat-square" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/kotlin-2.4.10-7F52FF?style=flat-square" alt="Kotlin 2.4.10">
  <img src="https://img.shields.io/github/actions/workflow/status/omersusin/piTube/build.yml?style=flat-square" alt="CI build">
  <img src="https://img.shields.io/badge/commits-372-0AC18E?style=flat-square" alt="372 commits">
  <img src="https://img.shields.io/badge/locales-28-0AC18E?style=flat-square" alt="28 locales">
  <img src="https://img.shields.io/badge/tests-393-0AC18E?style=flat-square" alt="393 tests">
</p>

piTube is a privacy-respecting, feature-rich YouTube client for Android. It is a fork of [Flow](https://github.com/A-EDev/Flow) with **high changes**: the codebase has been heavily reworked, dead code and legacy surfaces removed, and rebuilt around the [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) pipeline plus features researched and ported from the wider free-software YouTube ecosystem. Notable Flow-era surfaces stripped along the way: the TV/Leanback UI, Discord rich presence, RSS subscription feeds, the neural-engine/brain sync, and the music-library import.

---

## 📊 At a glance

Built in **12 days** of continuous development (August 8–20, 2026), with every feature verified against real YouTube responses and grounded in upstream research:

| Metric | Value |
|---|---|
| Commits | **372** — 199 fixes, 67 features, all on `main` |
| Codebase | ~143k lines of Kotlin across 672 files |
| Tests | **393** unit tests across 91 files + Room migration tests |
| Localization | **28 locales** (1,616 strings), incl. a complete Kabyle translation |
| Theming | 28 themes + a custom palette editor (36 color roles) |
| Launcher | 9 app-icon variants · 3 home-screen widgets |
| Storage | Room schema v27, encrypted profiles, DataStore |
| Targets | minSdk 26 · targetSdk 36 · compileSdk 37 |

---

## ✨ Features

<details open>
<summary><b>▶️ Player & playback</b></summary>

- **Enhanced player** — storyboard hover previews on the seek bar, double-tap to seek, background playback, speed control, audio-only mode, volume normalization, ambient mode, sleep timer, and a playback queue with **related-video radio mode**, **swipe-to-remove**, **restart-surviving persistence**, and **shuffle-all liked videos**
- **Crossfade between queue items** with enable/duration settings; configurable playback-notification buttons (like/dislike/radio); **most-played history sort**
- **Quality picker with bitrate labels** (Yattee-style) and a **synced-lyrics sheet** with live timestamp highlighting from YouTube's own `get_transcript` timed cues (InnerTune-style)
- **SABR streaming** — when YouTube serves a quality-incomplete ladder, piTube upgrades through the WEB + PoToken + SABR/UMP path (decoded natively) instead of settling for 720p
- **DLNA/UPnP casting** — cast to any smart TV, Kodi, VLC, or DLNA renderer through a local stream-proxy server, kept separate from local LAN sync traffic
- **Picture-in-picture & popup player** — PiP with playback controls, a resizable popup window, and automatic AppOps permission checks that guide you to system settings when PiP or overlay access is revoked
- **"Open in another player"** handoff from the quick-actions sheet, live chat polling, and a multi-client playback ladder with cookie-refresh rotation and session/playback resume across restarts

</details>

<details>
<summary><b>🔑 Accounts & watch history</b></summary>

- **Signed-in YouTube support** — like, subscribe, comments, playlists, and **real watch-history sync**: a Koda/yt-dlp-style `videostats` beacon reports your actual playback position (integer `cmt`/`st`/`et`, SAPISIDHASH-authenticated pings from the signed player response) so partially watched videos appear as in-progress in official YouTube history. The beacon follows YouTube's own heartbeat schedule — a start ping, periodic heartbeats, paused/ended states, and a final beacon committed when the app backgrounds
- **Multi-account login** — a **"You" tab** opens the redesigned account sheet (partial-height modal, profiles-first) for quick switching, with per-profile cookies, search history, and expired-session detection that flags dead profiles instead of silently falling back
- **Token/cookie-paste login** — an OuterTune/ViVi Music/ArchiveTune-style bundle (`***INNERTUBE COOKIE***`, `***VISITOR DATA***`, `***DATASYNC ID***`, `***PO TOKEN***`, `***ACCOUNT NAME/EMAIL/CHANNEL HANDLE***`) or a plain `SAPISID`/`SID` cookie header from a browser DevTools export — or the embedded WebView. **Add account** always waits for the async cookie purge before loading the login page (Koda fix `3691308` parity), so the old session can never silently recapture the previous account mid-flow
- **Real Watch Later & playlists** — toggles and the add-to-playlist sheet write to your actual account (`WL`/`PL…` via the WEB_REMIX client) when signed in, and "Create playlist" creates it on the account; a local offline-safe copy keeps everything working without a connection
- **Account library sync** — "Sync now" fully crawls liked videos (continuation-token pagination), playlists and subscriptions via the WEB client, with a silent daily **auto-sync**. The subscriptions crawl walks **every** page of FEchannels (shelf-wrapped renderers, continuation tokens) with transient-error retry and a repeated-token guard, so a sync never stops on a partial list
- **Notification inbox** — your real YouTube inbox (fetched via `notification/get_notification_menu`), refreshed on open and synced on a 12-hour schedule
- **Subscription transfer** — import/export from Settings as **NewPipe JSON**, a **Google Takeout CSV**, or **OPML** (Koda's SubscriptionTransfer)

</details>

<details>
<summary><b>💬 Comments & community</b></summary>

- **Koda-style panel** — a composer pinned at the bottom (replying-to banner, keyboard auto-focus), creator badges (channel-owner pill, verified check, pinned marker), the **creator's avatar + heart on hearted comments**, the channel-owner avatar on comments the creator replied to (LibreTube `viewRepliesCreatorThumbnail`), like pills, delete affordance for your own comments, and sort tabs (Top / Popular / Newest / Oldest). The composer stays above the keyboard on every show (IME-inset driven, `adjustResize`)
- **Channel community posts** with active-post previews, and **video collaborator resolution** (parallel avatar stacks on collab uploads)

</details>

<details>
<summary><b>🎤 Voice & song recognition</b></summary>

- The enlarged center icon on the icon-only bottom nav (the app's single search entry point) opens a Google voice-search-style modal
- **Voice mode** captures ~12 s and transcribes through a device-agnostic engine ladder: Puter's guest Whisper (`whisper-1`, network-first, no API key) → the verified on-device `SpeechRecognizer` (Android 12+) → Groq STT as last resort, with early stop on post-speech silence — the transcript is then **auto-submitted as a search**
- **Song mode** fingerprints the clip Shazam-style and recognizes it via Shazam (default, no key) or AudD/ACRCloud (build-time keys from `local.properties`), then searches the matched track; on failure the recording is saved locally and retried on reconnect
- Listening states are audio-reactive: an amplitude-driven **talking face** in Voice mode, a morphing gradient **blob** in Song mode — both drawn in Compose Canvas against live RMS. A floating overlay button (with notification) and a permission card complete the flow; the "Song Recognition" / "Şarkı Tanıma" settings section picks the provider and fallback policy

</details>

<details>
<summary><b>⬇️ Downloads</b></summary>

- Every download surface (player sheet, quick actions, library) funnels into one accordion-style **DownloadSheet** whose deterministic planner prefers the muxed stream (single start path, audio-only as one switch in the audio accordion, OPUS/M4A audio groups, codec chips)
- The queue screen offers **long-press multi-select with bulk pause/resume/retry/delete**, a per-item menu, retry for failed entries, and a completed section — design researched from Seal/YTDLnis
- Exactly **one notification per download** (unified start/resume/progress/terminal ids) that auto-dismisses on completion, with configurable notification actions
- **Filename templates, per-type folders, and subtitle/metadata sidecars** complete the pipeline

</details>

<details>
<summary><b>🌐 Translation</b></summary>

- Translate video titles, descriptions, comments, and channel/playlist names inline, with the original shown below in "dual" mode — or **double-tap any translated `SelectionContainer` text to flip it back to the original**
- Captions get native YouTube `tlang` tracks for perfectly synced translated subtitles
- Providers: the AI families (OpenRouter, OpenAI, Perplexity, Claude, Gemini, X.AI, Mistral, DeepL, custom OpenAI-compatible endpoints) and the complete Translate You engine set (Mozhi, LibreTranslate, Lingva, DeepL Authenticated free/paid API, DeepL Browser, Kagi, MyMemory, Yandex, SimplyTranslate, MinT, Glosbe, Apertium, OneRing, Pons, LaraTranslate), with the provider picker showing status notes that soft-deprecate engines currently broken at YouTube scale

</details>

<details>
<summary><b>🎨 Customization & content control</b></summary>

- **28 themes** (System Default, Material You, Pure Light, Mint Fresh, Rose Petal, Sky Blue, Cream Paper, Classic, True Black, Monochrome, Midnight, Deep Ocean, Forest, Lavender, Sunset, Nebula, Rose Gold, Arctic, Mint Night, Crimson, Cosmic Void, Solar Flare, Cyberpunk, Royal Gold, Nordic, Espresso, Gunmetal…) plus a **custom theme editor** for all 36 Material color roles
- **9 launcher icons** (red/light/play/AMOLED/monochrome/ghost/dynamic/Material Sky/Material Mint) switchable from Settings, RTL-aware back arrows, theme-aware icon colors in every theme
- **Content filtering** — block channels (persisted), **"Don't recommend this" dismissal on recommendations** with undo and a management screen, hide watched videos, hide unplayable videos, watched-threshold, shorts shelf toggles, per-surface options to hide counters/likes/comments
- Per-channel remembered default tab, configurable default navigation tab, grid-size/layout options, and **godmode** extras: shuffle-all liked videos and a queue that survives restarts

</details>

<details>
<summary><b>🔁 Device sync & connectivity</b></summary>

- **Device-to-device sync (FLOW-SYNC/1)** — sync watch progress, likes, playlists, subscriptions, settings and the recommendation profile between your own piTube installs over **local Wi-Fi only**: QR-code pairing (scan or show), an embedded **Ktor CIO WebSocket** server on an ephemeral port, **HKDF-SHA256** derived directional keys with **AES-256-GCM** per-frame sealing and a 6-digit SAS so you can verify the peer — byte-compatible with the Rust desktop implementation. A foreground service keeps transfers alive with the screen off; nothing leaves your network
- **Proxy settings** — route all remote traffic through an HTTP or SOCKS5 proxy (local casting and LAN discovery stay direct)
- **Deep links** — open YouTube *and* Piped links directly; share-text intents are accepted too
- Media-session based **Android Auto** support, keep-alive hints for Samsung/Xiaomi battery managers, and an **in-app update checker** pointed at the piTube GitHub releases

</details>

<details>
<summary><b>🧰 Home-screen widgets & utilities</b></summary>

- **Three Jetpack Glance widgets**: **Quick Actions** (search + jump to downloads/history/recognition), **Recently Played**, and **Downloads** (progress at a glance)
- **Time Management** — usage stats, bedtime reminders and screen-time controls with a dedicated settings screen
- **Diagnostics** — session logs and crash reports with copy/share, right from Settings
- **App update checker**, **Date & Time** display settings (payload-aware upload dates), **Buffer Settings**, notification preferences, and search-history controls
- **Consistent UI states** — every list shares the same loading/empty/error components (no blank screens, spinner+text footers), and all bottom sheets align with the app's canonical surface theme

</details>

<details>
<summary><b>🌍 Localization & feeds</b></summary>

- Full string parity across **28 locales**, including a **complete Kabyle (kab) translation**; the brand name and format tokens stay untranslated and intact in every language
- Home feed from YouTube's own "what to watch" endpoint with a rotation cursor and background refresh (instant first paint, time-boxed discovery), plus subscription, trending, and category feeds; localized view-count and relative-date parsing (e.g. Turkish *görüntüleme*/*B*/*önce*)
- Shorts feed with continuation-token paging and a hardened player client ladder; avatar/thumbnail URL normalization so channel images actually render
- Ad-free, trackless playback — no Google Ads, no Analytics SDKs

</details>

---

## 📅 Built in 12 days

The entire feature set above was researched, verified and shipped in under two weeks of commits — every milestone below is a real point in the repository history:

| Date | Milestone |
|---|---|
| **Aug 8** | Initial release — the Flow fork starts as a full YouTube client (77 commits on day one) |
| **Aug 9** | Dead Piped/NewPipeExtractor backend replaced with **direct InnerTube calls** (Koda pattern): SAPISIDHASH auth, layered stream resolution, REAL feeds |
| **Aug 10** | Renamed to **piTube** — package `io.github.aedev.flow` → `com.omersusin.pitube`, new logo, Flow UI integration |
| **Aug 11–12** | Localized metadata parsing (Turkish view counts/relatives), logo v2, history/like fixes |
| **Aug 13** | **SponsorBlock across all 10 categories** (+`poi_highlight`, custom colors), storyboard scrub preview, synced lyrics sheet, watch-history uplink; TV UI, Discord RP and RSS feeds removed; 1.7k lines of dead InnerTube code swept |
| **Aug 14** | **Multi-account backend** (encrypted profiles, switcher), token/cookie paste login, real Watch Later & playlist edits, notification inbox, Translate You + AI provider port |
| **Aug 15** | **Koda-style comments redesign** (creator hearted/replied badges, IME-safe composer), **voice & song recognition** (Shazam/AudD/ACRCloud + Puter Whisper), double-tap translation flip, DeepL Authenticated |
| **Aug 16** | **Unified DownloadSheet** — deterministic muxed-first planner, long-press queue management, exactly one notification per download; shared loading/empty/error states |
| **Aug 17** | **Real-time watch history** (YouTube heartbeat cadence, paused/ended beacons), complete **Kabyle translation** + 28-locale parity, godmode filtering, shuffle-all liked, persistent queue |
| **Aug 18** | **Radio mode, crossfade, swipe-to-remove queue**; subscription import/export (NewPipe JSON / Takeout CSV / OPML); configurable notification buttons; most-played sort |
| **Aug 19** | Auth correctness: `datasyncId`/`onBehalfOfUser` 401 fix with regression test, add-account fresh-login race (Koda `3691308` parity), hardened multi-page FEchannels crawl |
| **Aug 20** | CI uploads **one APK per ABI split** (universal + arm64-v8a + armeabi-v7a + x86_64 + x86, debug and release) |

---

## 🏗️ Architecture & tech stack

| Layer | Choice |
|---|---|
| UI | Jetpack Compose (Material 3, BOM 2026.06.01), Navigation Compose, ConstraintLayout Compose, Coil 3 image loading |
| DI | Hilt 2.60.1 (KSP, aggregating task) |
| Networking | OkHttp 5.4 + Ktor 3.5.2 client, KotlinX Serialization, Brotli, re2j, Conscrypt TLS |
| Extraction | NewPipeExtractor v0.26.4 with a direct **InnerTube** layer (Koda-style pages, signed requests) + WebView-generated **PoToken** |
| Storage | Room 2.8.4 (schema v27, migration-tested), DataStore Preferences, Security-Crypto (encrypted profile store) |
| Playback | Media3 ExoPlayer 1.11.0 (HLS/DASH/progressive), SABR/UMP decoder, custom download/notification services |
| Embedded servers | Ktor CIO WebSocket server (device sync), local stream-proxy server (DLNA cast) |
| Background | WorkManager (auto-sync, notification inbox, heartbeat flush), foreground services (sync, recognition, playback) |
| Async | Kotlin Coroutines 1.11 + Flow/StateFlow, Paging 3, Jetpack Glance widgets |
| Quality | Spotless/ktlint (Kotlin + Gradle scripts), 393 unit tests + Room migration tests, CI gate |

Development research briefs (multi-account backend, translation provider audit, download-plane design, UI-UX port decisions) are kept in [`research-papers/`](research-papers/).

---

## 🔧 Building

The project uses Gradle with a version catalog (`gradle/libs.versions.toml`), Kotlin 2.4.10, and the Compose compiler plugin.

```bash
./gradlew assembleDebug      # debug APK
./gradlew assembleNightly    # release-grade build, debug-signed, -nightly suffix
./gradlew assembleRelease    # release APK (unsigned when no keystore is configured — F-Droid friendly)
```

Each task emits a **universal APK plus one per ABI** (arm64-v8a, armeabi-v7a, x86, x86_64). Release metadata (dependency info) is stripped from APKs for IzzyOnDroid/F-Droid eligibility.

Release signing uses environment variables (no secrets in the repo):

| Variable | Description |
|---|---|
| `KEYSTORE_FILE` | Path to the release keystore (`~/.keystore/piTube-release.jks`) |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias (e.g. `pitube`) |
| `KEY_PASSWORD` | Key password |

### CI

`.github/workflows/build.yml` runs on every push/PR to `main`: it decodes the signing keystore from the `KEYSTORE_BASE64` secret, runs `testDebugUnitTest`, builds debug + release, and uploads **10 artifacts** — universal and per-ABI APKs for both build types (`piTube-debug-*` / `piTube-release-*`), each failing loudly if the expected file is missing.

### Testing & code quality

```bash
./gradlew testDebugUnitTest   # 393 unit tests (feed parsing, SABR policy, sync crypto, translation, ViewModels…)
./gradlew ktlintCheck         # Spotless/ktlint verification
./gradlew ktlintFormat        # auto-format
```

Room schema exports live in `app/schemas` with migration tests under `androidTest`.

---

## 🤝 Credits & acknowledgements

piTube is a Flow fork with high changes and builds on the shoulders of the free-software YouTube ecosystem. Research, ports, and design inspiration came from:

- **[Flow](https://github.com/A-EDev/Flow)** — upstream project piTube is forked from (Compose UI, architecture). The device-sync protocol (FLOW-SYNC/1), Glance widgets, SABR streaming, DLNA casting and poToken plumbing were inherited from this lineage and extended here rather than re-invented
- **[Koda](https://github.com/Ivorisnoob/Koda)** — WEB-client account reads (subscribed channels, playlists, liked videos), avatar resolution upgrade, watchtime ping auth (SAPISIDHASH), the multi-account profile backend (per-profile cookies, account switching, session invalidation), **cookie-paste login**, **expired-session detection**, the **real Watch Later / playlist-edit** path (`edit_playlist`/`WL` and `playlist/create` on the music origin), the **notification inbox** parser, the **comments panel design** (bottom composer with a replying-to banner, creator pill and verified badges, like pill with creator heart, down-arrow reply expansion), the **add-account fresh-login fix** (async cookie removal before the login page opens, `3691308`), **datasyncId bookkeeping** (signed requests never send `user.onBehalfOfUser`), the **subscription-transfer formats** (NewPipe JSON, Takeout CSV, OPML), the **"Don't recommend this" / Not-interested flow** with its management screen, and the **multi-client playback ladder**, **cookie-refresh rotation**, and **session/playback resume** across restarts
- **[NewPipe / NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)** — the extraction core (channels, streams, tabs, signed requests, signature timestamp handling) and the **FEchannels subscription crawl** (shelf-wrapped channel lists, full multi-page pagination)
- **[yt-dlp](https://github.com/yt-dlp/yt-dlp)** — watch-history beacon logic (`videostatsPlaybackUrl`/`videostatsWatchtimeUrl`, `ver=2`/`cpn`/`cmt`/`el=detailpage` params) ported to report real partial positions, plus YouTube's own heartbeat cadence (start ping, periodic pings, paused/ended, final beacon on app background)
- **[SponsorBlock](https://sponsor.ajay.app)** — crowd-sourced segment skipping (10 categories incl. `poi_highlight`), DeArrow, and RYD data
- **[ViMusic](https://github.com/vipulnsward/ViMusic) / [InnerTune](https://github.com/z-huang/InnerTune) / [OuterTune](https://github.com/outertune/outertune) / [Metrolist](https://github.com/abdlquadri/Metrolist) / [ArchiveTune](https://github.com/Archivist-ai/ArchiveTune) / [audiotube](https://github.com/nichpan/audiotube)** — music-client lineage: playback registration, library browsing, media-session patterns, and the synced-lyrics `get_transcript` path
- **[ViVi Music](https://github.com/vivizzz007/vivi-music)** — the AI translation providers (OpenRouter, OpenAI, Perplexity, Claude, Gemini, X.AI, Mistral, DeepL, custom OpenAI-compatible endpoints) and, together with OuterTune and ArchiveTune, the `***INNERTUBE COOKIE***`-style session-token bundle format powering the cookie-paste login
- **[Audile](https://github.com/AudileTeam/Audile)** — the song-recognition backend (Shazam signature generation, AudD and ACRCloud providers with embedded keys) and its offline save-and-retry fallback policy
- **[HeyPuter/puter](https://github.com/HeyPuter/puter)** — the `speech2txt` guest-auth + `/drivers/call` flow re-implemented in Kotlin for the Whisper voice transcription (no API key)
- **[LibreTube](https://github.com/libre-tube/LibreTube)** — design patterns for settings and per-channel behavior, plus the comments creator badges: the channel-owner avatar shown when the uploader hearted a comment or replied to it (`viewRepliesCreatorThumbnail`)
- **[Piped](https://github.com/TeamPiped/Piped)** — API-first client patterns
- **[Invidious](https://github.com/iv-org/invidious)** — feed/community insights
- **[Grayjay](https://gitlab.futo.org/videostreaming/grayjay)** — external player integration ideas
- **[ReVanced](https://gitlab.com/ReVanced/revanced-patches)** — content-filtering and SponsorBlock/DeArrow/RYD integration patterns
- **[ViewTube](https://github.com/ViewTube/viewtube)** — SponsorBlock server-side category handling incl. `poi_highlight`
- **[FlexTube](https://github.com/FlexTube) / [Beatbump](https://github.com/snuffyDev/Beatbump) / [ViewerTube](https://github.com/wartek-dev/viewertube)** — frontend/UX references
- **[Seal](https://github.com/JunkFood02/Seal) / [YTDLnis](https://github.com/deniscerri/ytdlnis)** — download-plane design research: unification of download entry points, muxed-first stream planning, and queue-management UX, plus filename templates, per-type folders, and sidecar metadata/subtitles
- **[Yattee](https://github.com/yattee/yattee)** — the bitrate/size format-details shown in the quality picker
- **[Translate You](https://github.com/you-apps/TranslateYou)** — the translation engine abstraction and its free engines (Mozhi, LibreTranslate, Lingva, MyMemory, MinT, Kagi, OneRing, Yandex, Pons, Glosbe, Apertium, LaraTranslate, SimplyTranslate, DeepL browser) re-implemented on Ktor for the in-app translation feature

## 📄 License

piTube is licensed under the [GPL-3.0](LICENSE) — same as Flow and NewPipeExtractor. Use, modify, and share freely.

---

*piTube is not affiliated with YouTube or Google. All product names, logos, and brands are property of their respective owners.*